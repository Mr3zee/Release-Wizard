package com.github.mr3zee.webhooks

import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ReleaseId
import kotlin.time.Instant

data class PendingWebhook(
    val id: String,
    val externalId: String,
    val blockId: BlockId,
    val releaseId: ReleaseId,
    val connectionId: ConnectionId,
    val type: WebhookType,
    val status: WebhookStatus,
    val payload: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class WebhookType {
    TEAMCITY,
    GITHUB,
}

enum class WebhookStatus {
    PENDING,
    COMPLETED,
    FAILED,
}
