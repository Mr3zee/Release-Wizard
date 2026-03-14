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
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    companion object {
        // Pre-computed Argon2 hash used to normalize timing when user not found
        private val DUMMY_HASH: String = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
            .hash(3, 65536, 1, "dummy-password".toCharArray())
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toUser(): User = User(
        id = UserId(this[UserTable.id].value.toString()),
        username = this[UserTable.username],
        role = UserRole.valueOf(this[UserTable.role]),
    )

    override suspend fun validate(username: String, password: String): User? = dbQuery {
        val row = UserTable.selectAll()
            .where { UserTable.username eq username }
            .singleOrNull() ?: run {
            // Prevent timing-based user enumeration by performing a dummy verification
            argon2.verify(DUMMY_HASH, password.toCharArray())
            log.warn("Login failed for user '{}': not found", username)
            return@dbQuery null
        }
        val hash = row[UserTable.passwordHash]
        if (argon2.verify(hash, password.toCharArray())) {
            log.info("Login succeeded for user '{}'", username)
            row.toUser()
        } else {
            log.warn("Login failed for user '{}': wrong password", username)
            null
        }
    }

    override suspend fun register(username: String, password: String): User? =
        withContext(Dispatchers.IO) {
            suspendTransaction(db, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                val existing = UserTable.selectAll()
                    .where { UserTable.username eq username }
                    .singleOrNull()
                if (existing != null) {
                    // Normalize timing so duplicate-username path takes roughly the same time as success path
                    argon2.hash(3, 65536, 1, "dummy-timing-normalization".toCharArray())
                    return@suspendTransaction null
                }

                val isFirstUser = UserTable.selectAll().count() == 0L
                val role = if (isFirstUser) UserRole.ADMIN else UserRole.USER

                val id = UUID.randomUUID()
                val now = Clock.System.now()
                val hash = argon2.hash(3, 65536, 1, password.toCharArray())
                UserTable.insert {
                    it[UserTable.id] = id
                    it[UserTable.username] = username
                    it[UserTable.passwordHash] = hash
                    it[UserTable.role] = role.name
                    it[UserTable.createdAt] = now
                }

                User(id = UserId(id.toString()), username = username, role = role)
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
            it[UserTable.role] = role.name
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
                    val isTargetCurrentlyAdmin = targetRow[UserTable.role] == UserRole.ADMIN.name
                    if (isTargetCurrentlyAdmin) {
                        val adminCount = UserTable.selectAll()
                            .where { UserTable.role eq UserRole.ADMIN.name }
                            .count()
                        if (adminCount <= 1) {
                            return@suspendTransaction Result.failure(
                                IllegalStateException("Cannot demote the last admin")
                            )
                        }
                    }
                }

                val updated = UserTable.update({ UserTable.id eq UUID.fromString(id.value) }) {
                    it[UserTable.role] = role.name
                }
                Result.success(updated > 0)
            }
        }
}
