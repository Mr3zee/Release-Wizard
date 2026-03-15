package com.github.mr3zee.persistence

import com.github.mr3zee.webhooks.WebhookStatus
import com.github.mr3zee.webhooks.WebhookType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object PendingWebhookTable : UUIDTable("pending_webhooks") {
    val externalId = varchar("external_id", 255)
    val blockId = varchar("block_id", 255)
    val releaseId = reference("release_id", ReleaseTable, onDelete = ReferenceOption.CASCADE)
    val connectionId = reference("connection_id", ConnectionTable, onDelete = ReferenceOption.RESTRICT)
    val type = enumerationByName<WebhookType>("type", 32)
    val status = enumerationByName<WebhookStatus>("status", 32)
    val payload = text("payload").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    init {
        index("idx_webhook_external_type", false, externalId, type)
        index("idx_webhook_release_status", false, releaseId, status)
        index("idx_webhook_release_block", false, releaseId, blockId)
        index("idx_webhook_connection_type", false, connectionId, type)
        index("idx_webhook_status_updated", false, status, updatedAt)
    }
}
