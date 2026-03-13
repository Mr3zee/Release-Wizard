package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object PendingWebhookTable : UUIDTable("pending_webhooks") {
    val externalId = varchar("external_id", 255)
    val blockId = varchar("block_id", 255)
    val releaseId = varchar("release_id", 36)
    val connectionId = varchar("connection_id", 36)
    val type = varchar("type", 32)
    val status = varchar("status", 32)
    val payload = text("payload").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
