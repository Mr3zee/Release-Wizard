package com.github.mr3zee.auth

import com.github.mr3zee.model.User
import com.github.mr3zee.model.UserId
import com.github.mr3zee.model.UserRole
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
}
