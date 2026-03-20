package com.github.mr3zee.auth

import com.github.mr3zee.model.UserId
import com.github.mr3zee.persistence.PasswordResetTokenTable
import com.github.mr3zee.persistence.UserTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

data class GeneratedToken(val rawToken: String, val expiresAtMillis: Long)

interface PasswordResetService {
    /**
     * Generate a password reset token for the target user.
     * Invalidates any existing active tokens for that user.
     * Returns the raw (unhashed) token and its expiry on success.
     */
    suspend fun generateToken(targetUserId: UserId, createdByUserId: UserId): Result<GeneratedToken>

    /**
     * Validate whether a raw token is still valid (exists, not used, not expired).
     * Returns the target user's ID if valid, null otherwise.
     */
    suspend fun validateToken(rawToken: String): UserId?

    /**
     * Consume a token: mark it as used, update the user's password hash and passwordChangedAt.
     * Returns the target user's ID on success.
     */
    suspend fun consumeToken(rawToken: String, newPasswordHash: String): Result<UserId>
}

class DatabasePasswordResetService(private val db: Database) : PasswordResetService {
    private val log = LoggerFactory.getLogger(DatabasePasswordResetService::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        private val TOKEN_EXPIRY = 24.hours
        private const val TOKEN_BYTE_LENGTH = 64
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override suspend fun generateToken(targetUserId: UserId, createdByUserId: UserId): Result<GeneratedToken> {
        val targetUuid = try {
            UUID.fromString(targetUserId.value)
        } catch (_: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("Invalid user ID format"))
        }

        val rawToken = generateRawToken()
        val tokenHash = hashToken(rawToken)
        val now = Clock.System.now()
        val expiresAt = now + TOKEN_EXPIRY

        // Atomic: verify user exists + invalidate old tokens + insert new token in one transaction
        val created = dbQuery {
            val userExists = UserTable.selectAll()
                .where { UserTable.id eq targetUuid }
                .count() > 0
            if (!userExists) return@dbQuery false

            // Invalidate existing active tokens for this user
            PasswordResetTokenTable.update(
                where = {
                    (PasswordResetTokenTable.userId eq targetUuid) and
                        PasswordResetTokenTable.usedAt.isNull()
                }
            ) {
                it[PasswordResetTokenTable.usedAt] = now
            }

            // Insert new token
            PasswordResetTokenTable.insert {
                it[PasswordResetTokenTable.id] = UUID.randomUUID()
                it[PasswordResetTokenTable.tokenHash] = tokenHash
                it[PasswordResetTokenTable.userId] = targetUuid
                it[PasswordResetTokenTable.createdByUserId] = UUID.fromString(createdByUserId.value)
                it[PasswordResetTokenTable.expiresAt] = expiresAt
                it[PasswordResetTokenTable.createdAt] = now
            }
            true
        }
        if (!created) {
            return Result.failure(IllegalArgumentException("User not found"))
        }

        log.info("Password reset token generated for user '{}' by '{}'", targetUserId.value, createdByUserId.value)
        return Result.success(GeneratedToken(rawToken, expiresAt.toEpochMilliseconds()))
    }

    override suspend fun validateToken(rawToken: String): UserId? {
        if (rawToken.isBlank()) return null
        val tokenHash = hashToken(rawToken)
        val now = Clock.System.now()

        return dbQuery {
            val row = PasswordResetTokenTable.selectAll()
                .where {
                    (PasswordResetTokenTable.tokenHash eq tokenHash) and
                        PasswordResetTokenTable.usedAt.isNull() and
                        (PasswordResetTokenTable.expiresAt greater now)
                }
                .singleOrNull() ?: return@dbQuery null

            UserId(row[PasswordResetTokenTable.userId].value.toString())
        }
    }

    override suspend fun consumeToken(rawToken: String, newPasswordHash: String): Result<UserId> {
        if (rawToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Token must not be blank"))
        }
        val tokenHash = hashToken(rawToken)
        val now = Clock.System.now()

        return dbQuery {
            // Find and validate the token
            val row = PasswordResetTokenTable.selectAll()
                .where {
                    (PasswordResetTokenTable.tokenHash eq tokenHash) and
                        PasswordResetTokenTable.usedAt.isNull() and
                        (PasswordResetTokenTable.expiresAt greater now)
                }
                .forUpdate()
                .singleOrNull()
                ?: return@dbQuery Result.failure(IllegalArgumentException("Invalid or expired token"))

            val targetUserId = row[PasswordResetTokenTable.userId].value

            // Mark token as used
            PasswordResetTokenTable.update(
                where = { PasswordResetTokenTable.id eq row[PasswordResetTokenTable.id].value }
            ) {
                it[PasswordResetTokenTable.usedAt] = now
            }

            // Update user's password and passwordChangedAt
            UserTable.update({ UserTable.id eq targetUserId }) {
                it[UserTable.passwordHash] = newPasswordHash
                it[UserTable.passwordChangedAt] = now
            }

            log.info("Password reset token consumed for user '{}'", targetUserId)
            Result.success(UserId(targetUserId.toString()))
        }
    }
}
