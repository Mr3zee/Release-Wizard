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
    /** TEAM-H5: Invite expiry — PENDING invites older than this are invalid */
    val expiresAt = timestamp("expires_at").nullable()

    init {
        index(false, invitedUserId)
        // TEAM-H2: The old uniqueIndex(teamId, invitedUserId, status) over-constrained
        // historical records. Pending uniqueness is enforced by a partial index in DatabaseFactory.
    }
}
