package com.github.mr3zee.auth

import com.github.mr3zee.NotApprovedException
import com.github.mr3zee.PatConfig
import com.github.mr3zee.api.CreatePatResponse
import com.github.mr3zee.api.PatInfo
import com.github.mr3zee.model.ClientType
import com.github.mr3zee.model.UserId
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PatService(
    private val repository: PatRepository,
    private val config: PatConfig,
) {
    private val log = LoggerFactory.getLogger(PatService::class.java)

    companion object {
        const val TOKEN_PREFIX = "rwpat_"
        private const val TOKEN_BYTE_LENGTH = 32
        private val LAST_USED_DEBOUNCE = 1.minutes
        val CLEANUP_INTERVAL = 24.hours
    }

    suspend fun create(userId: UserId, name: String, expiresInDays: Int?): CreatePatResponse {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Token name must not be blank" }
        require(trimmedName.length <= 100) { "Token name must not exceed 100 characters" }

        if (expiresInDays != null) {
            require(expiresInDays in 1..config.maxExpiryDays) {
                "Expiry must be between 1 and ${config.maxExpiryDays} days"
            }
        }

        val rawToken = TokenUtils.generateRawToken(TOKEN_BYTE_LENGTH)
        val tokenHash = TokenUtils.hashToken(rawToken)
        val now = Clock.System.now()
        val expiresAt = expiresInDays?.let { now + it.days }

        // Atomic count+insert in a single transaction to prevent TOCTOU race
        val record = repository.createIfUnderLimit(
            userId = UUID.fromString(userId.value),
            name = trimmedName,
            tokenHash = tokenHash,
            expiresAt = expiresAt,
            maxPerUser = config.maxPerUser,
        ) ?: error("Maximum number of active tokens (${config.maxPerUser}) reached")

        log.info("PAT created for user '{}' (tokenId={})", userId.value, record.id)

        return CreatePatResponse(
            id = record.id.toString(),
            name = record.name,
            token = TOKEN_PREFIX + rawToken,
            expiresAt = expiresAt?.toEpochMilliseconds(),
            createdAt = record.createdAt.toEpochMilliseconds(),
        )
    }

    /**
     * Validate a raw PAT token and return a [UserSession] if valid.
     *
     * Returns null for tokens with invalid format or that don't exist.
     * Throws [NotApprovedException] for unapproved users (caught by StatusPages → 403).
     */
    suspend fun validate(rawToken: String): UserSession? {
        if (!rawToken.startsWith(TOKEN_PREFIX)) return null

        val tokenBody = rawToken.removePrefix(TOKEN_PREFIX)
        if (tokenBody.length != TOKEN_BYTE_LENGTH * 2) return null

        val tokenHash = TokenUtils.hashToken(tokenBody)
        val result = repository.findActiveByTokenHash(tokenHash)

        if (result == null) {
            log.debug("PAT validation failed: token not found or expired")
            return null
        }

        // Check if password was changed after PAT creation (mirrors SessionTtl behavior)
        val pwChangedAt = result.passwordChangedAt
        if (pwChangedAt != null && result.pat.createdAt < pwChangedAt) {
            log.debug("PAT validation failed: token predates password change for user '{}'", result.username)
            return null
        }

        // Enforce approval gate — plugin skips PAT requests, so we check here
        if (!result.userApproved) {
            log.debug("PAT validation failed: user '{}' is not approved", result.username)
            throw NotApprovedException()
        }

        // Debounced lastUsedAt update
        val threshold = Clock.System.now() - LAST_USED_DEBOUNCE
        repository.updateLastUsedAt(result.pat.id, threshold)

        return UserSession(
            username = result.username,
            userId = result.pat.userId.toString(),
            role = result.userRole,
            csrfToken = "", // PAT auth has no CSRF — CsrfPlugin skips when no session cookie
            clientType = ClientType.DESKTOP, // PAT users are API/CLI clients, not browser sessions
            createdAt = result.pat.createdAt.toEpochMilliseconds(),
            lastAccessedAt = Clock.System.now().toEpochMilliseconds(),
            approved = true,
        )
    }

    suspend fun listByUser(userId: UserId): List<PatInfo> =
        repository.listByUser(userId).map { it.toPatInfo() }

    suspend fun revoke(patId: String, ownerUserId: UserId): Boolean {
        val uuid = try {
            UUID.fromString(patId)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val revoked = repository.revoke(uuid, ownerUserId)
        if (revoked) {
            log.info("PAT revoked by owner (tokenId={}, userId={})", patId, ownerUserId.value)
        }
        return revoked
    }

    suspend fun adminRevoke(patId: String): Boolean {
        val uuid = try {
            UUID.fromString(patId)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val revoked = repository.revoke(uuid, ownerUserId = null)
        if (revoked) {
            log.info("PAT revoked by admin (tokenId={})", patId)
        }
        return revoked
    }

    suspend fun deleteExpiredAndRevoked() {
        val olderThan = Clock.System.now() - 30.days
        val deleted = repository.deleteExpiredAndRevoked(olderThan)
        if (deleted > 0) {
            log.info("Cleaned up {} expired/revoked PATs", deleted)
        }
    }

    private fun PatRecord.toPatInfo() = PatInfo(
        id = id.toString(),
        name = name,
        expiresAt = expiresAt?.toEpochMilliseconds(),
        lastUsedAt = lastUsedAt?.toEpochMilliseconds(),
        createdAt = createdAt.toEpochMilliseconds(),
        revoked = revokedAt != null,
    )
}
