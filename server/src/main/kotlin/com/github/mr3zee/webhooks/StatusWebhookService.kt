package com.github.mr3zee.webhooks

import com.github.mr3zee.WebhookConfig
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.StatusUpdatePayload
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.releases.ReleasesRepository
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class StatusWebhookService(
    private val tokenRepository: StatusWebhookTokenRepository,
    private val releasesRepository: ReleasesRepository,
    private val executionEngineProvider: Lazy<ExecutionEngine>,
    private val webhookConfig: WebhookConfig,
) {
    private val log = LoggerFactory.getLogger(StatusWebhookService::class.java)

    // HOOK-M6: Normalize baseUrl trailing slash to prevent double-slash in URL
    fun webhookUrl(): String = "${webhookConfig.baseUrl.trimEnd('/')}${ApiRoutes.Webhooks.STATUS}"

    suspend fun createToken(releaseId: ReleaseId, blockId: BlockId): UUID {
        return tokenRepository.create(releaseId, blockId)
    }

    suspend fun deactivateToken(releaseId: ReleaseId, blockId: BlockId) {
        tokenRepository.deactivate(releaseId, blockId)
    }

    suspend fun processStatusUpdate(token: UUID, payload: StatusUpdatePayload): StatusWebhookResult {
        // Validate payload BEFORE consuming the token, so callers can retry on BadRequest
        val status = payload.status.trim()
        if (status.isEmpty()) {
            return StatusWebhookResult.BadRequest("Status must not be empty")
        }

        // HOOK-H1: Atomically find + validate + deactivate in a single transaction.
        // The token is deactivated inside the same DB transaction to prevent replay attacks
        // via concurrent requests.
        val tokenRecord = tokenRepository.findAndDeactivateToken(token, TOKEN_TTL)
            ?: return StatusWebhookResult.NotFound

        val truncatedStatus = status.take(MAX_STATUS_LENGTH)
        val truncatedDescription = payload.description?.take(MAX_DESCRIPTION_LENGTH)

        val updated = releasesRepository.updateWebhookStatus(
            releaseId = tokenRecord.releaseId,
            blockId = tokenRecord.blockId,
            status = truncatedStatus,
            description = truncatedDescription,
            receivedAt = Clock.System.now(),
        )

        if (updated == null) {
            // HOOK-H2: Block is not RUNNING — return distinct status (410 Gone) instead of generic 404
            return StatusWebhookResult.BlockNotRunning
        }

        executionEngineProvider.value.emitBlockUpdate(tokenRecord.releaseId, updated)
        log.debug("Status webhook update accepted: release={} block={} status={}",
            tokenRecord.releaseId.value, tokenRecord.blockId.value, truncatedStatus)

        return StatusWebhookResult.Accepted
    }

    suspend fun cleanupExpiredTokens() {
        val now = Clock.System.now()
        val deactivated = tokenRepository.deactivateExpiredBefore(now - TOKEN_TTL)
        if (deactivated > 0) {
            log.info("Deactivated {} expired status webhook tokens", deactivated)
        }

        val deleted = tokenRepository.deleteInactiveBefore(now - CLEANUP_AGE)
        if (deleted > 0) {
            log.info("Deleted {} old inactive status webhook tokens", deleted)
        }
    }

    companion object {
        val TOKEN_TTL = 24.hours
        val CLEANUP_AGE = 30.days
        /** HOOK-M7: How often the periodic cleanup job runs */
        val CLEANUP_INTERVAL = 1.hours
        const val MAX_STATUS_LENGTH = 200
        const val MAX_DESCRIPTION_LENGTH = 1000
    }
}

sealed class StatusWebhookResult {
    data object Accepted : StatusWebhookResult()
    data object NotFound : StatusWebhookResult()
    /** HOOK-H2: Block exists but is not in RUNNING state — distinct from token-not-found */
    data object BlockNotRunning : StatusWebhookResult()
    data class BadRequest(val message: String) : StatusWebhookResult()
}
