package com.github.mr3zee.auth

import com.github.mr3zee.api.OAuthProvider
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.model.User
import com.github.mr3zee.model.UserId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.persistence.AccountLockoutTable
import com.github.mr3zee.persistence.OAuthAccountTable
import com.github.mr3zee.persistence.TeamMembershipTable
import com.github.mr3zee.persistence.UserTable
import de.mkammerer.argon2.Argon2Factory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.nio.CharBuffer
import java.sql.Connection
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock

interface AuthService {
    suspend fun validate(username: String, password: String): User?
    suspend fun register(username: String, password: String): User?
    suspend fun getUserById(id: UserId): User?
    suspend fun getUserByUsername(username: String): User?
    suspend fun listUsers(): List<User>
    suspend fun updateUserRole(id: UserId, role: UserRole): Boolean

    /**
     * Atomically checks that demoting [id] would not remove the last admin,
     * then applies the role change. Returns:
     * - [Result.success] with `true` if updated, `false` if user not found
     * - [Result.failure] with [IllegalStateException] if demoting the last admin
     */
    suspend fun safeUpdateUserRole(id: UserId, role: UserRole): Result<Boolean>

    /**
     * Change the username for a user. If [currentPassword] is non-null, validates it first.
     * If null, the user must have no password (OAuth-only).
     */
    suspend fun changeUsername(userId: UserId, newUsername: String, currentPassword: String?): Result<User>

    /**
     * Change or set the password for a user. If [currentPassword] is non-null, validates it first.
     * If null, the user must have no password (OAuth-only "set password" flow).
     */
    suspend fun changePassword(userId: UserId, currentPassword: String?, newPassword: String): Result<Boolean>

    /**
     * Safely delete a user account. Validates confirmation username.
     * If [currentPassword] is non-null, validates it. If null, the user must have no password (OAuth-only).
     * Checks that the user is not the last admin or last team lead in any team.
     */
    suspend fun deleteAccountSafe(userId: UserId, confirmUsername: String, currentPassword: String?): Result<Boolean>

    /**
     * Hash a password using the configured Argon2 parameters.
     * Used by password reset flow where the route needs to pre-hash before passing to service.
     */
    suspend fun hashPassword(password: String): String

    /**
     * Lightweight query for session refresh: returns role, username, and passwordChangedAt
     * without constructing a full User object. Returns null if user not found (deleted).
     */
    suspend fun getSessionRefreshInfo(id: UserId): SessionRefreshInfo?
}

data class SessionRefreshInfo(
    val role: UserRole,
    val username: String,
    val passwordChangedAtMillis: Long?,
)

class DatabaseAuthService(
    private val db: Database,
    private val pepperSecret: ByteArray? = null,
    private val pepperSecretOld: ByteArray? = null,
) : AuthService {
    private val log = LoggerFactory.getLogger(DatabaseAuthService::class.java)

    // AUTH-H3: Argon2 instance is thread-safe — argon2-jvm uses stateless JNI calls
    // with per-invocation native buffers; no mutable shared state.
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    companion object {
        // AUTH-H5: Argon2 parallelism p=4 (OWASP minimum recommendation)
        private const val ARGON2_ITERATIONS = 3
        private const val ARGON2_MEMORY_KB = 65536
        private const val ARGON2_PARALLELISM = 4
    }

    // Pre-computed Argon2 hash used to normalize timing when user not found.
    // Instance-level lazy (not companion) because the hash must incorporate the pepper.
    private val dummyHash: String by lazy {
        argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM,
            preparePassword("dummy-password".toCharArray()))
    }

    init {
        // Eagerly compute to avoid first-request latency spike (~400ms Argon2)
        dummyHash
    }

    /**
     * Apply HMAC-SHA256 peppering to a password before Argon2 hashing/verification.
     * Uses CharBuffer.wrap to avoid creating an intermediate String from the password.
     */
    private fun pepperPassword(password: CharArray, secret: ByteArray): CharArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val passwordBytes = Charsets.UTF_8.encode(CharBuffer.wrap(password)).let { buf ->
            ByteArray(buf.remaining()).also { buf.get(it) }
        }
        try {
            val hmacBytes = mac.doFinal(passwordBytes)
            return Base64.getEncoder().encodeToString(hmacBytes).toCharArray()
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun preparePassword(password: CharArray): CharArray {
        val secret = pepperSecret ?: return password
        return pepperPassword(password, secret)
    }

    /**
     * Verify a password against a stored hash, with fallback to old pepper for rotation.
     * If the old pepper matches, transparently re-hashes with the current pepper.
     */
    private suspend fun verifyPasswordWithRotation(storedHash: String, password: String, userId: UserId): Boolean {
        val prepared = preparePassword(password.toCharArray())
        try {
            val valid = withContext(Dispatchers.IO) { argon2.verify(storedHash, prepared) }
            if (valid) return true

            if (pepperSecretOld != null) {
                val preparedOld = pepperPassword(password.toCharArray(), pepperSecretOld)
                try {
                    val validOld = withContext(Dispatchers.IO) { argon2.verify(storedHash, preparedOld) }
                    if (validOld) {
                        // Transparently re-hash with current pepper
                        val newHash = withContext(Dispatchers.IO) {
                            argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, prepared)
                        }
                        dbQuery {
                            UserTable.update({ UserTable.id eq UUID.fromString(userId.value) }) {
                                it[UserTable.passwordHash] = newHash
                            }
                        }
                        log.info("Password hash upgraded to current pepper for user '{}'", userId.value)
                        return true
                    }
                } finally {
                    preparedOld.fill(0.toChar())
                }
            }

            return false
        } finally {
            prepared.fill(0.toChar())
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toUser(): User = User(
        id = UserId(this[UserTable.id].value.toString()),
        username = this[UserTable.username],
        role = this[UserTable.role],
        createdAt = this[UserTable.createdAt].toEpochMilliseconds(),
        hasPassword = this[UserTable.passwordHash] != null,
    )

    override suspend fun validate(username: String, password: String): User? {
        // DB-H1: Read user row in a short transaction, then verify hash outside to avoid
        // holding a DB connection during the ~400ms Argon2 computation.
        val row = dbQuery {
            UserTable.selectAll()
                .where { UserTable.username eq username }
                .singleOrNull()
        }
        if (row == null) {
            // Prevent timing-based user enumeration by performing a dummy verification
            withContext(Dispatchers.IO) { argon2.verify(dummyHash, preparePassword(password.toCharArray())) }
            // AUTH-L2: Uniform log message for all login failures to prevent user enumeration via logs
            log.warn("Login failed for user '{}'", username)
            return null
        }
        val hash = row[UserTable.passwordHash]
        if (hash == null) {
            // OAuth-only user — cannot authenticate with password. Perform dummy hash for timing.
            withContext(Dispatchers.IO) { argon2.verify(dummyHash, preparePassword(password.toCharArray())) }
            log.warn("Login failed for user '{}'", username)
            return null
        }
        val userId = UserId(row[UserTable.id].value.toString())
        val valid = verifyPasswordWithRotation(hash, password, userId)
        return if (valid) {
            log.info("Login succeeded for user '{}'", username)
            row.toUser()
        } else {
            // AUTH-L2: Same message as user-not-found to prevent log-based enumeration
            log.warn("Login failed for user '{}'", username)
            null
        }
    }

    override suspend fun register(username: String, password: String): User? {
        // AUTH-M1: Pre-compute the password hash OUTSIDE the SERIALIZABLE transaction
        // to avoid holding the DB lock during the ~400ms Argon2 computation.
        val hash = withContext(Dispatchers.IO) {
            argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, preparePassword(password.toCharArray()))
        }
        return withContext(Dispatchers.IO) {
            try {
                suspendTransaction(db, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                    val existing = UserTable.selectAll()
                        .where { UserTable.username eq username }
                        .singleOrNull()
                    if (existing != null) {
                        return@suspendTransaction null
                    }

                    val isFirstUser = UserTable.selectAll().count() == 0L
                    val role = if (isFirstUser) UserRole.ADMIN else UserRole.USER

                    val id = UUID.randomUUID()
                    val now = Clock.System.now()
                    UserTable.insert {
                        it[UserTable.id] = id
                        it[UserTable.username] = username
                        it[UserTable.passwordHash] = hash
                        it[UserTable.role] = role
                        it[UserTable.createdAt] = now
                    }

                    User(id = UserId(id.toString()), username = username, role = role)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // AUTH-M1: Catch unique constraint violation from concurrent registrations.
                // With hash computed outside the transaction, two concurrent registrations
                // can both pass the SELECT check, then one fails at INSERT with a constraint violation.
                if (e.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.message?.contains("duplicate key", ignoreCase = true) == true ||
                    e.cause?.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.cause?.message?.contains("duplicate key", ignoreCase = true) == true
                ) {
                    log.debug("Concurrent registration detected for username '{}'", username)
                    null
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun getUserById(id: UserId): User? = dbQuery {
        UserTable.selectAll()
            .where { UserTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun getUserByUsername(username: String): User? = dbQuery {
        UserTable.selectAll()
            .where { UserTable.username eq username }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun listUsers(): List<User> = dbQuery {
        val users = UserTable.selectAll()
            .orderBy(UserTable.createdAt, SortOrder.ASC)
            .map { it.toUser() }

        // Fetch OAuth providers for all users in one query
        val oauthByUserId = OAuthAccountTable.selectAll()
            .groupBy { it[OAuthAccountTable.userId].value.toString() }
            .mapValues { (_, rows) ->
                rows.mapNotNull { row ->
                    when (row[OAuthAccountTable.provider]) {
                        OAuthProvider.GOOGLE.name.lowercase() -> OAuthProvider.GOOGLE
                        else -> null
                    }
                }
            }

        users.map { user ->
            user.copy(oauthProviders = oauthByUserId[user.id.value].orEmpty())
        }
    }

    override suspend fun updateUserRole(id: UserId, role: UserRole): Boolean = dbQuery {
        val updated = UserTable.update({ UserTable.id eq UUID.fromString(id.value) }) {
            it[UserTable.role] = role
        }
        updated > 0
    }

    override suspend fun safeUpdateUserRole(id: UserId, role: UserRole): Result<Boolean> =
        withContext(Dispatchers.IO) {
            suspendTransaction(db, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                // Check last-admin constraint atomically within the same transaction
                if (role != UserRole.ADMIN) {
                    val targetRow = UserTable.selectAll()
                        .where { UserTable.id eq UUID.fromString(id.value) }
                        .singleOrNull()
                        ?: return@suspendTransaction Result.success(false)
                    val isTargetCurrentlyAdmin = targetRow[UserTable.role] == UserRole.ADMIN
                    if (isTargetCurrentlyAdmin) {
                        val adminCount = UserTable.selectAll()
                            .where { UserTable.role eq UserRole.ADMIN }
                            .count()
                        if (adminCount <= 1) {
                            return@suspendTransaction Result.failure(
                                IllegalStateException("Cannot demote the last admin")
                            )
                        }
                    }
                }

                val updated = UserTable.update({ UserTable.id eq UUID.fromString(id.value) }) {
                    it[UserTable.role] = role
                }
                Result.success(updated > 0)
            }
        }

    /**
     * Fetches a user row by ID and verifies the password outside a transaction.
     * If the user has no password (OAuth-only) and [password] is null, verification is skipped.
     * If the user has a password, [password] must be non-null and correct.
     */
    private suspend fun fetchUserAndVerifyPassword(userId: UserId, password: String?): Result<ResultRow> {
        val userRow = dbQuery {
            UserTable.selectAll()
                .where { UserTable.id eq UUID.fromString(userId.value) }
                .singleOrNull()
        } ?: return Result.failure(IllegalArgumentException("User not found"))

        val storedHash = userRow[UserTable.passwordHash]
        if (storedHash != null) {
            // User has a password — must verify it
            if (password == null) {
                return Result.failure(IllegalArgumentException("INVALID_PASSWORD"))
            }
            if (!verifyPasswordWithRotation(storedHash, password, userId)) {
                return Result.failure(IllegalArgumentException("INVALID_PASSWORD"))
            }
        }
        // OAuth-only users (storedHash == null) skip password verification
        return Result.success(userRow)
    }

    override suspend fun changeUsername(userId: UserId, newUsername: String, currentPassword: String?): Result<User> {
        fetchUserAndVerifyPassword(userId, currentPassword)
            .getOrElse { return Result.failure(it) }

        // Validate username format
        val trimmed = newUsername.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("VALIDATION_ERROR:Username must not be blank"))
        }
        if (trimmed.length > 64) {
            return Result.failure(IllegalArgumentException("VALIDATION_ERROR:Username must not exceed 64 characters"))
        }

        // Atomically check uniqueness + update in SERIALIZABLE transaction
        return withContext(Dispatchers.IO) {
            try {
                suspendTransaction(db, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                    val existing = UserTable.selectAll()
                        .where { UserTable.username eq trimmed }
                        .singleOrNull()
                    if (existing != null && existing[UserTable.id].value.toString() != userId.value) {
                        return@suspendTransaction Result.failure(
                            IllegalArgumentException("USERNAME_TAKEN")
                        )
                    }

                    UserTable.update({ UserTable.id eq UUID.fromString(userId.value) }) {
                        it[UserTable.username] = trimmed
                    }

                    val updated = UserTable.selectAll()
                        .where { UserTable.id eq UUID.fromString(userId.value) }
                        .single()
                        .toUser()
                    Result.success(updated)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                if (e.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.message?.contains("duplicate key", ignoreCase = true) == true ||
                    e.cause?.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.cause?.message?.contains("duplicate key", ignoreCase = true) == true
                ) {
                    Result.failure(IllegalArgumentException("USERNAME_TAKEN"))
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun changePassword(userId: UserId, currentPassword: String?, newPassword: String): Result<Boolean> {
        val userRow = fetchUserAndVerifyPassword(userId, currentPassword)
            .getOrElse { return Result.failure(it) }
        val storedHash = userRow[UserTable.passwordHash]

        // Re-read current hash after verification — rotation may have updated it
        val currentHash = if (storedHash != null) {
            dbQuery {
                UserTable.selectAll()
                    .where { UserTable.id eq UUID.fromString(userId.value) }
                    .singleOrNull()
                    ?.get(UserTable.passwordHash)
            }
        } else {
            null
        }

        // Hash new password outside the transaction
        val newHash = withContext(Dispatchers.IO) {
            argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, preparePassword(newPassword.toCharArray()))
        }

        // DB-H2: Use conditional UPDATE to prevent TOCTOU race — only update if the hash
        // hasn't been changed by another concurrent request (e.g., password reset).
        val updated = dbQuery {
            val now = Clock.System.now()
            if (currentHash != null) {
                UserTable.update({
                    (UserTable.id eq UUID.fromString(userId.value)) and
                        (UserTable.passwordHash eq currentHash)
                }) {
                    it[UserTable.passwordHash] = newHash
                    it[UserTable.passwordChangedAt] = now
                }
            } else {
                // OAuth-only user setting password for the first time (passwordHash IS NULL)
                UserTable.update({
                    (UserTable.id eq UUID.fromString(userId.value)) and
                        (UserTable.passwordHash.isNull())
                }) {
                    it[UserTable.passwordHash] = newHash
                    it[UserTable.passwordChangedAt] = now
                }
            }
        }
        if (updated == 0) {
            return Result.failure(IllegalArgumentException("INVALID_PASSWORD"))
        }

        log.info("Password changed for user '{}'", userId.value)
        return Result.success(true)
    }

    override suspend fun deleteAccountSafe(
        userId: UserId,
        confirmUsername: String,
        currentPassword: String?,
    ): Result<Boolean> {
        val userRow = fetchUserAndVerifyPassword(userId, currentPassword)
            .getOrElse { return Result.failure(it) }

        val actualUsername = userRow[UserTable.username]
        if (confirmUsername != actualUsername) {
            return Result.failure(IllegalArgumentException("USERNAME_MISMATCH"))
        }

        return withContext(Dispatchers.IO) {
            suspendTransaction(db, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                // Re-read role inside transaction to avoid TOCTOU race
                val currentRow = UserTable.selectAll()
                    .where { UserTable.id eq UUID.fromString(userId.value) }
                    .singleOrNull()
                    ?: return@suspendTransaction Result.success(false)
                val isAdmin = currentRow[UserTable.role] == UserRole.ADMIN
                if (isAdmin) {
                    val adminCount = UserTable.selectAll()
                        .where { UserTable.role eq UserRole.ADMIN }
                        .count()
                    if (adminCount <= 1) {
                        return@suspendTransaction Result.failure(
                            IllegalStateException("LAST_ADMIN")
                        )
                    }
                }

                // Check last team lead constraint per team
                val uuid = UUID.fromString(userId.value)
                val userTeamLeaderships = TeamMembershipTable.selectAll()
                    .where {
                        (TeamMembershipTable.userId eq uuid) and
                            (TeamMembershipTable.role eq TeamRole.TEAM_LEAD)
                    }
                    .map { it[TeamMembershipTable.teamId].value }

                for (teamId in userTeamLeaderships) {
                    val leadCount = TeamMembershipTable.selectAll()
                        .where {
                            (TeamMembershipTable.teamId eq teamId) and
                                (TeamMembershipTable.role eq TeamRole.TEAM_LEAD)
                        }
                        .count()
                    if (leadCount <= 1) {
                        return@suspendTransaction Result.failure(
                            IllegalStateException("LAST_TEAM_LEAD")
                        )
                    }
                }

                // Delete user (CASCADE handles memberships, invites, etc.)
                UserTable.deleteWhere { UserTable.id eq uuid }

                // Clean up AccountLockoutTable rows (no FK cascade)
                AccountLockoutTable.deleteWhere { AccountLockoutTable.username eq actualUsername }

                Result.success(true)
            }
        }
    }

    override suspend fun hashPassword(password: String): String =
        withContext(Dispatchers.IO) {
            argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, preparePassword(password.toCharArray()))
        }

    override suspend fun getSessionRefreshInfo(id: UserId): SessionRefreshInfo? = dbQuery {
        UserTable.selectAll()
            .where { UserTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.let { row ->
                SessionRefreshInfo(
                    role = row[UserTable.role],
                    username = row[UserTable.username],
                    passwordChangedAtMillis = row[UserTable.passwordChangedAt]?.toEpochMilliseconds(),
                )
            }
    }
}
