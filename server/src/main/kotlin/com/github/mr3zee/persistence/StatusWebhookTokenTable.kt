package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object StatusWebhookTokenTable : UUIDTable("status_webhook_tokens") {
    val releaseId = reference("release_id", ReleaseTable, onDelete = ReferenceOption.CASCADE)
    val blockId = varchar("block_id", 255)
    val active = bool("active").default(true)
    val createdAt = timestamp("created_at")

    init {
        index("idx_swt_release_block", false, releaseId, blockId)
    }
}
