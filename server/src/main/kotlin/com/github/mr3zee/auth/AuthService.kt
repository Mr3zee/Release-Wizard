package com.github.mr3zee.auth

import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.model.User
import com.github.mr3zee.model.UserId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.persistence.AccountLockoutTable
import com.github.mr3zee.persistence.TeamMembershipTable
import com.github.mr3zee.persistence.UserTable
import de.mkammerer.argon2.Argon2Factory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID
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
     * Change the username for a user. Validates the current password first.
     * Returns:
     * - [Result.success] with updated [User] on success
     * - [Result.failure] with [IllegalArgumentException] for validation errors (INVALID_PASSWORD, USERNAME_TAKEN, VALIDATION_ERROR)
     */
    suspend fun changeUsername(userId: UserId, newUsername: String, currentPassword: String): Result<User>

    /**
     * Change the password for a user. Validates the current password first.
     * Returns:
     * - [Result.success] with `true` on success
     * - [Result.failure] with [IllegalArgumentException] for INVALID_PASSWORD
     */
    suspend fun changePassword(userId: UserId, currentPassword: String, newPassword: String): Result<Boolean>

    /**
     * Safely delete a user account. Validates current password and confirmation username.
     * Checks that the user is not the last admin or last team lead in any team.
     * Returns:
     * - [Result.success] with `true` on success
     * - [Result.failure] with [IllegalStateException] for LAST_ADMIN, LAST_TEAM_LEAD
     * - [Result.failure] with [IllegalArgumentException] for INVALID_PASSWORD, USERNAME_MISMATCH
     */
    suspend fun deleteAccountSafe(userId: UserId, confirmUsername: String, currentPassword: String): Result<Boolean>

    /**
     * Hash a password using the configured Argon2 parameters.
     * Used by password reset flow where the route needs to pre-hash before passing to service.
     */
    suspend fun hashPassword(password: String): String
}

class DatabaseAuthService(private val db: Database) : AuthService {
    private val log = LoggerFactory.getLogger(DatabaseAuthService::class.java)

    // AUTH-H3: Argon2 instance is thread-safe — argon2-jvm uses stateless JNI calls
    // with per-invocation native buffers; no mutable shared state.
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    companion object {
        // AUTH-H5: Argon2 parallelism p=4 (OWASP minimum recommendation)
        private const val ARGON2_ITERATIONS = 3
        private const val ARGON2_MEMORY_KB = 65536
        private const val ARGON2_PARALLELISM = 4

        // Pre-computed Argon2 hash used to normalize timing when user not found
        private val DUMMY_HASH: String = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
            .hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, "dummy-password".toCharArray())
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toUser(): User = User(
        id = UserId(this[UserTable.id].value.toString()),
        username = this[UserTable.username],
        role = this[UserTable.role],
        createdAt = this[UserTable.createdAt].toEpochMilliseconds(),
    )

    override suspend fun validate(username: String, password: String): User? = dbQuery {
        val row = UserTable.selectAll()
            .where { UserTable.username eq username }
            .singleOrNull() ?: run {
            // Prevent timing-based user enumeration by performing a dummy verification
            argon2.verify(DUMMY_HASH, password.toCharArray())
            // AUTH-L2: Uniform log message for all login failures to prevent user enumeration via logs
            log.warn("Login failed for user '{}'", username)
            return@dbQuery null
        }
        val hash = row[UserTable.passwordHash]
        if (argon2.verify(hash, password.toCharArray())) {
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
            argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, password.toCharArray())
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
        UserTable.selectAll()
            .orderBy(UserTable.createdAt, SortOrder.ASC)
            .map { it.toUser() }
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

    override suspend fun changeUsername(userId: UserId, newUsername: String, currentPassword: String): Result<User> {
        // Validate password outside the transaction (timing-safe)
        val userRow = dbQuery {
            UserTable.selectAll()
                .where { UserTable.id eq UUID.fromString(userId.value) }
                .singleOrNull()
        } ?: return Result.failure(IllegalArgumentException("User not found"))

        val storedHash = userRow[UserTable.passwordHash]
        val passwordValid = withContext(Dispatchers.IO) {
            argon2.verify(storedHash, currentPassword.toCharArray())
        }
        if (!passwordValid) {
            return Result.failure(IllegalArgumentException("INVALID_PASSWORD"))
        }

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

    override suspend fun changePassword(userId: UserId, currentPassword: String, newPassword: String): Result<Boolean> {
        // Validate current password outside the transaction
        val userRow = dbQuery {
            UserTable.selectAll()
                .where { UserTable.id eq UUID.fromString(userId.value) }
                .singleOrNull()
        } ?: return Result.failure(IllegalArgumentException("User not found"))

        val storedHash = userRow[UserTable.passwordHash]
        val passwordValid = withContext(Dispatchers.IO) {
            argon2.verify(storedHash, currentPassword.toCharArray())
        }
        if (!passwordValid) {
            return Result.failure(IllegalArgumentException("INVALID_PASSWORD"))
        }

        // Hash new password outside the transaction
        val newHash = withContext(Dispatchers.IO) {
            argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, newPassword.toCharArray())
        }

        dbQuery {
            val now = Clock.System.now()
            UserTable.update({ UserTable.id eq UUID.fromString(userId.value) }) {
                it[UserTable.passwordHash] = newHash
                it[UserTable.passwordChangedAt] = now
            }
        }

        log.info("Password changed for user '{}'", userId.value)
        return Result.success(true)
    }

    override suspend fun deleteAccountSafe(
        userId: UserId,
        confirmUsername: String,
        currentPassword: String,
    ): Result<Boolean> {
        // Validate password outside the transaction
        val userRow = dbQuery {
            UserTable.selectAll()
                .where { UserTable.id eq UUID.fromString(userId.value) }
                .singleOrNull()
        } ?: return Result.failure(IllegalArgumentException("User not found"))

        val storedHash = userRow[UserTable.passwordHash]
        val passwordValid = withContext(Dispatchers.IO) {
            argon2.verify(storedHash, currentPassword.toCharArray())
        }
        if (!passwordValid) {
            return Result.failure(IllegalArgumentException("INVALID_PASSWORD"))
        }

        val actualUsername = userRow[UserTable.username]
        if (confirmUsername != actualUsername) {
            return Result.failure(IllegalArgumentException("USERNAME_MISMATCH"))
        }

        return withContext(Dispatchers.IO) {
            suspendTransaction(db, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                // Check last admin constraint
                val isAdmin = userRow[UserTable.role] == UserRole.ADMIN
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
                            (TeamMembershipTable.role eq TeamRole.LEAD)
                    }
                    .map { it[TeamMembershipTable.teamId].value }

                for (teamId in userTeamLeaderships) {
                    val leadCount = TeamMembershipTable.selectAll()
                        .where {
                            (TeamMembershipTable.teamId eq teamId) and
                                (TeamMembershipTable.role eq TeamRole.LEAD)
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
            argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, password.toCharArray())
        }
}
