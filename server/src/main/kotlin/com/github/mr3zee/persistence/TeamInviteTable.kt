package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object TeamInviteTable : UUIDTable("team_invites") {
    val teamId = varchar("team_id", 36)
    val invitedUserId = varchar("invited_user_id", 36)
    val invitedByUserId = varchar("invited_by_user_id", 36)
    val status = varchar("status", 32)
    val createdAt = timestamp("created_at")

    init {
        index(false, teamId)
        index(false, invitedUserId)
    }
}
