package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuditAction {
    // Team
    TEAM_CREATED,
    TEAM_UPDATED,
    TEAM_DELETED,

    // Membership
    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_REMOVED,
    MEMBER_ROLE_CHANGED,
    INVITE_SENT,
    INVITE_ACCEPTED,
    INVITE_DECLINED,
    INVITE_CANCELLED,
    JOIN_REQUEST_SUBMITTED,
    JOIN_REQUEST_APPROVED,
    JOIN_REQUEST_REJECTED,

    // Projects
    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_DELETED,

    // Releases
    RELEASE_STARTED,
    RELEASE_CANCELLED,
    RELEASE_RERUN,
    RELEASE_ARCHIVED,
    RELEASE_DELETED,
    BLOCK_RESTARTED,
    BLOCK_APPROVED,

    // Connections
    CONNECTION_CREATED,
    CONNECTION_UPDATED,
    CONNECTION_DELETED,

    // Schedules
    SCHEDULE_CREATED,
    SCHEDULE_UPDATED,
    SCHEDULE_DELETED,

    // Triggers
    TRIGGER_CREATED,
    TRIGGER_UPDATED,
    TRIGGER_DELETED,
    TRIGGER_FIRED,

    // Tags
    TAG_RENAMED,
    TAG_DELETED,

    // Auth
    USER_LOGIN,
    USER_REGISTER,
}

@Serializable
enum class AuditTargetType {
    TEAM,
    USER,
    PROJECT,
    RELEASE,
    BLOCK,
    CONNECTION,
    SCHEDULE,
    TRIGGER,
    TAG,
}

@Serializable
data class AuditEvent(
    val id: String,
    val teamId: TeamId? = null,
    val actorUserId: UserId,
    val actorUsername: String,
    val action: AuditAction,
    val targetType: AuditTargetType,
    val targetId: String,
    val details: String = "",
    val timestamp: Long = 0,
)
