package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class InviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED,
}

@Serializable
data class TeamInvite(
    val id: String,
    val teamId: TeamId,
    val teamName: String = "",
    val invitedUserId: UserId,
    val invitedUsername: String = "",
    val invitedByUserId: UserId,
    val invitedByUsername: String = "",
    val status: InviteStatus = InviteStatus.PENDING,
    val createdAt: Long = 0,
)
