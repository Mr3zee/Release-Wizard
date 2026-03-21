package com.github.mr3zee.auth

import com.github.mr3zee.api.OAuthProvider
import com.github.mr3zee.model.User
import com.github.mr3zee.model.UserId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.persistence.OAuthAccountTable
import com.github.mr3zee.persistence.UserTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID
import kotlin.time.Clock

interface OAuthService {
    suspend fun findOrCreateOAuthUser(
        provider: String,
        providerUserId: String,
        email: String?,
        displayName: String?,
    ): User

    suspend fun getOAuthProviders(userId: UserId): List<OAuthProvider>

    suspend fun hasPassword(userId: UserId): Boolean
}

class DatabaseOAuthService(private val db: Database) : OAuthService {
    private val log = LoggerFactory.getLogger(DatabaseOAuthService::class.java)

    override suspend fun findOrCreateOAuthUser(
        provider: String,
        providerUserId: String,
        email: String?,
        displayName: String?,
    ): User {
        return withContext(Dispatchers.IO) {
            try {
                suspendTransaction(db, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                    // Check if this OAuth identity is already linked
                    val existingLink = OAuthAccountTable.selectAll()
                        .where {
                            (OAuthAccountTable.provider eq provider) and
                                (OAuthAccountTable.providerUserId eq providerUserId)
                        }
                        .singleOrNull()

                    if (existingLink != null) {
                        val userId = existingLink[OAuthAccountTable.userId].value
                        val userRow = UserTable.selectAll()
                            .where { UserTable.id eq userId }
                            .single()
                        return@suspendTransaction User(
                            id = UserId(userId.toString()),
                            username = userRow[UserTable.username],
                            role = userRow[UserTable.role],
                            createdAt = userRow[UserTable.createdAt].toEpochMilliseconds(),
                        )
                    }

                    // Create a new user
                    val isFirstUser = UserTable.selectAll().count() == 0L
                    val role = if (isFirstUser) UserRole.ADMIN else UserRole.USER
                    val username = findAvailableUsername(generateUsername(email, displayName))
                    val userId = UUID.randomUUID()
                    val now = Clock.System.now()

                    UserTable.insert {
                        it[UserTable.id] = userId
                        it[UserTable.username] = username
                        it[UserTable.passwordHash] = null
                        it[UserTable.role] = role
                        it[UserTable.createdAt] = now
                    }

                    OAuthAccountTable.insert {
                        it[OAuthAccountTable.id] = UUID.randomUUID()
                        it[OAuthAccountTable.userId] = userId
                        it[OAuthAccountTable.provider] = provider
                        it[OAuthAccountTable.providerUserId] = providerUserId
                        it[OAuthAccountTable.email] = email
                        it[OAuthAccountTable.displayName] = displayName
                        it[OAuthAccountTable.createdAt] = now
                    }

                    log.info("Created OAuth user '{}' via {} with role {}", username, provider, role)

                    User(
                        id = UserId(userId.toString()),
                        username = username,
                        role = role,
                        createdAt = now.toEpochMilliseconds(),
                    )
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Handle concurrent creation race — the unique constraint on oauth_accounts
                // will reject one of the concurrent inserts. Retry by looking up the existing link.
                if (e.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.message?.contains("duplicate key", ignoreCase = true) == true ||
                    e.cause?.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.cause?.message?.contains("duplicate key", ignoreCase = true) == true
                ) {
                    log.debug("Concurrent OAuth account creation detected for {} {}", provider, providerUserId)
                    return@withContext suspendTransaction(db) {
                        val link = OAuthAccountTable.selectAll()
                            .where {
                                (OAuthAccountTable.provider eq provider) and
                                    (OAuthAccountTable.providerUserId eq providerUserId)
                            }
                            .single()
                        val userId = link[OAuthAccountTable.userId].value
                        val userRow = UserTable.selectAll()
                            .where { UserTable.id eq userId }
                            .single()
                        User(
                            id = UserId(userId.toString()),
                            username = userRow[UserTable.username],
                            role = userRow[UserTable.role],
                            createdAt = userRow[UserTable.createdAt].toEpochMilliseconds(),
                        )
                    }
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun getOAuthProviders(userId: UserId): List<OAuthProvider> =
        withContext(Dispatchers.IO) {
            suspendTransaction(db) {
                OAuthAccountTable.selectAll()
                    .where { OAuthAccountTable.userId eq UUID.fromString(userId.value) }
                    .map { row ->
                        when (row[OAuthAccountTable.provider]) {
                            OAuthProvider.GOOGLE.name.lowercase() -> OAuthProvider.GOOGLE
                            else -> null
                        }
                    }
                    .filterNotNull()
            }
        }

    override suspend fun hasPassword(userId: UserId): Boolean =
        withContext(Dispatchers.IO) {
            suspendTransaction(db) {
                UserTable.selectAll()
                    .where { UserTable.id eq UUID.fromString(userId.value) }
                    .singleOrNull()
                    ?.let { it[UserTable.passwordHash] != null }
                    ?: false
            }
        }

    companion object {
        private val VALID_USERNAME_CHARS = Regex("[^a-zA-Z0-9._-]")

        internal fun generateUsername(email: String?, displayName: String?): String {
            val raw = when {
                displayName != null -> {
                    VALID_USERNAME_CHARS.replace(displayName.replace(" ", "."), "")
                }
                email != null -> {
                    VALID_USERNAME_CHARS.replace(email.substringBefore("@"), "")
                }
                else -> "user"
            }
            // Trim leading/trailing dots, hyphens, underscores and collapse consecutive dots
            val sanitized = raw
                .replace(Regex("\\.{2,}"), ".")
                .trim('.', '-', '_')
                .take(60)
                .ifBlank { "user" }
            return sanitized.lowercase()
        }

        /**
         * Finds an available username by appending numeric suffixes if needed.
         * Must be called inside a transaction.
         */
        internal fun findAvailableUsername(base: String): String {
            val existing = UserTable.selectAll()
                .where { UserTable.username eq base }
                .singleOrNull()
            if (existing == null) return base

            for (i in 1..999) {
                val candidate = "$base$i"
                val taken = UserTable.selectAll()
                    .where { UserTable.username eq candidate }
                    .singleOrNull()
                if (taken == null) return candidate
            }
            // Fallback: append random suffix
            return "${base}_${UUID.randomUUID().toString().take(6)}"
        }
    }
}
