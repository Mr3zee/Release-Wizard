package com.github.mr3zee.persistence

import com.github.mr3zee.model.InviteStatus
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object TeamInviteTable : UUIDTable("team_invites") {
    val teamId = reference("team_id", TeamTable, onDelete = ReferenceOption.CASCADE)
    val invitedUserId = reference("invited_user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val invitedByUserId = reference("invited_by_user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val status = enumerationByName<InviteStatus>("status", 32)
    val createdAt = timestamp("created_at")

    init {
        index(false, invitedUserId)
        uniqueIndex("uq_team_invite_team_user_status", teamId, invitedUserId, status)
    }
}
