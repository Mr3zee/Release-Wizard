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
    private val executionEngine: ExecutionEngine,
    private val webhookConfig: WebhookConfig,
) {
    private val log = LoggerFactory.getLogger(StatusWebhookService::class.java)

    fun webhookUrl(): String = "${webhookConfig.baseUrl}${ApiRoutes.Webhooks.STATUS}"

    suspend fun createToken(releaseId: ReleaseId, blockId: BlockId): UUID {
        return tokenRepository.create(releaseId, blockId)
    }

    suspend fun deactivateToken(releaseId: ReleaseId, blockId: BlockId) {
        tokenRepository.deactivate(releaseId, blockId)
    }

    suspend fun processStatusUpdate(token: UUID, payload: StatusUpdatePayload): StatusWebhookResult {
        val tokenRecord = tokenRepository.findByToken(token)
            ?: return StatusWebhookResult.NotFound

        if (!tokenRecord.active) {
            return StatusWebhookResult.NotFound
        }

        // Check 24h absolute TTL
        val age = Clock.System.now() - tokenRecord.createdAt
        if (age > TOKEN_TTL) {
            log.info("Status webhook token {} expired (age={})", token, age)
            tokenRepository.deactivate(tokenRecord.releaseId, tokenRecord.blockId)
            return StatusWebhookResult.NotFound
        }

        val status = payload.status.trim()
        if (status.isEmpty()) {
            return StatusWebhookResult.BadRequest("Status must not be empty")
        }

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
            // Block is not RUNNING — atomic WHERE condition failed
            return StatusWebhookResult.NotFound
        }

        executionEngine.emitBlockUpdate(tokenRecord.releaseId, updated)
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
        const val MAX_STATUS_LENGTH = 200
        const val MAX_DESCRIPTION_LENGTH = 1000
    }
}

sealed class StatusWebhookResult {
    data object Accepted : StatusWebhookResult()
    data object NotFound : StatusWebhookResult()
    data class BadRequest(val message: String) : StatusWebhookResult()
}
