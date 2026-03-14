package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object AuditEventTable : UUIDTable("audit_events") {
    val teamId = varchar("team_id", 36).nullable()
    val actorUserId = varchar("actor_user_id", 36)
    val actorUsername = varchar("actor_username", 255)
    val action = varchar("action", 64)
    val targetType = varchar("target_type", 32)
    val targetId = varchar("target_id", 255)
    val details = text("details").default("")
    val timestamp = timestamp("timestamp")

    init {
        index(false, teamId, this.timestamp)
    }
}
