package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserNotificationType {
    APPROVAL_REQUESTED,
    RELEASE_COMPLETED,
    TEAM_INVITE_RECEIVED,
    JOIN_REQUEST_DECIDED,
    JOIN_REQUEST_RECEIVED,
    MEMBER_ROLE_CHANGED,
    ACCOUNT_PENDING_APPROVAL,
    UNKNOWN,
}

@Serializable
data class UserNotification(
    val id: String,
    val userId: String,
    val type: UserNotificationType,
    val teamId: TeamId? = null,
    val teamName: String? = null,
    val title: String,
    val message: String,
    val targetType: String? = null,
    val targetId: String? = null,
    val read: Boolean = false,
    val timestamp: Long = 0,
)
