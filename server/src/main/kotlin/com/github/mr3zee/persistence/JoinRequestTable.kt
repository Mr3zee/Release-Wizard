package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object JoinRequestTable : UUIDTable("join_requests") {
    val teamId = varchar("team_id", 36)
    val userId = varchar("user_id", 36)
    val status = varchar("status", 32)
    val reviewedByUserId = varchar("reviewed_by_user_id", 36).nullable()
    val createdAt = timestamp("created_at")
    val reviewedAt = timestamp("reviewed_at").nullable()

    init {
        index(false, teamId)
        index(false, userId)
    }
}
