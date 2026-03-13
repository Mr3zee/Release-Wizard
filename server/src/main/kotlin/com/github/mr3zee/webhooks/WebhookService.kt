package com.github.mr3zee.webhooks

import com.github.mr3zee.connections.ConnectionsRepository
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Handles incoming webhook events from external services (TeamCity, GitHub).
 * Validates secrets, updates PendingWebhook records, and emits notifications.
 */
class WebhookService(
    private val webhookRepository: PendingWebhookRepository,
    private val connectionsRepository: ConnectionsRepository,
) {
    private val log = LoggerFactory.getLogger(WebhookService::class.java)
    private val _completions = MutableSharedFlow<WebhookCompletion>(
        extraBufferCapacity = 64,
    )
    val completions: SharedFlow<WebhookCompletion> = _completions.asSharedFlow()

    /**
     * Process a TeamCity webhook callback.
     * TeamCity sends X-Webhook-Secret header for validation.
     */
    suspend fun processTeamCityWebhook(
        connectionId: ConnectionId,
        webhookSecret: String?,
        payload: String,
    ): WebhookResult {
        log.debug("Processing TeamCity webhook for connection {}", connectionId.value)
        val connection = connectionsRepository.findById(connectionId)
            ?: return WebhookResult.ConnectionNotFound

        val config = connection.config as? ConnectionConfig.TeamCityConfig
            ?: return WebhookResult.InvalidConnectionType

        if (config.webhookSecret.isNotEmpty()) {
            if (webhookSecret != config.webhookSecret) {
                log.warn("Invalid webhook secret for TeamCity connection {}", connectionId.value)
                return WebhookResult.InvalidSecret
            }
        }

        return completeMatchingWebhooks(connectionId, WebhookType.TEAMCITY, payload)
    }

    /**
     * Process a GitHub webhook callback.
     * GitHub sends X-Hub-Signature-256 header (HMAC-SHA256) for validation.
     */
    suspend fun processGitHubWebhook(
        connectionId: ConnectionId,
        signatureHeader: String?,
        payload: String,
    ): WebhookResult {
        log.debug("Processing GitHub webhook for connection {}", connectionId.value)
        val connection = connectionsRepository.findById(connectionId)
            ?: return WebhookResult.ConnectionNotFound

        val config = connection.config as? ConnectionConfig.GitHubConfig
            ?: return WebhookResult.InvalidConnectionType

        if (config.webhookSecret.isNotEmpty()) {
            if (signatureHeader == null) {
                log.warn("Missing signature for GitHub webhook on connection {}", connectionId.value)
                return WebhookResult.InvalidSecret
            }
            if (!verifyGitHubSignature(payload, signatureHeader, config.webhookSecret)) {
                log.warn("Invalid signature for GitHub webhook on connection {}", connectionId.value)
                return WebhookResult.InvalidSecret
            }
        }

        return completeMatchingWebhooks(connectionId, WebhookType.GITHUB, payload)
    }

    private suspend fun completeMatchingWebhooks(
        connectionId: ConnectionId,
        type: WebhookType,
        payload: String,
    ): WebhookResult {
        val pendingWebhooks = webhookRepository.findByConnectionIdAndType(connectionId, type)
        if (pendingWebhooks.isEmpty()) {
            return WebhookResult.NoPendingWebhooks
        }

        for (webhook in pendingWebhooks) {
            log.info("Webhook matched: block={} release={} type={}", webhook.blockId.value, webhook.releaseId.value, type)
            webhookRepository.updateStatus(webhook.id, WebhookStatus.COMPLETED, payload)
            _completions.emit(
                WebhookCompletion(
                    webhookId = webhook.id,
                    blockId = webhook.blockId,
                    releaseId = webhook.releaseId,
                    type = type,
                    payload = payload,
                )
            )
        }

        return WebhookResult.Accepted
    }

    /**
     * Emit a completion event directly. Intended for testing.
     */
    suspend fun emitCompletion(completion: WebhookCompletion) {
        _completions.emit(completion)
    }

    companion object {
        fun verifyGitHubSignature(payload: String, signatureHeader: String, secret: String): Boolean {
            if (!signatureHeader.startsWith("sha256=")) return false
            val expectedHex = signatureHeader.removePrefix("sha256=")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            val computed = mac.doFinal(payload.toByteArray())
            val computedHex = computed.joinToString("") { "%02x".format(it) }
            return MessageDigest.isEqual(expectedHex.toByteArray(), computedHex.toByteArray())
        }
    }
}

data class WebhookCompletion(
    val webhookId: String,
    val blockId: com.github.mr3zee.model.BlockId,
    val releaseId: com.github.mr3zee.model.ReleaseId,
    val type: WebhookType,
    val payload: String,
)

enum class WebhookResult {
    Accepted,
    ConnectionNotFound,
    InvalidConnectionType,
    InvalidSecret,
    NoPendingWebhooks,
}
