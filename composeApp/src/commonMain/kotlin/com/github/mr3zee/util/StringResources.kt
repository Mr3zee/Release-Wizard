package com.github.mr3zee.util

import androidx.compose.runtime.Composable
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockStatus
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.AuditAction
import com.github.mr3zee.model.AuditTargetType
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.i18n.packStringResource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant
import releasewizard.composeapp.generated.resources.*

internal fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return buildString {
        if (h > 0) append("${h}h ")
        if (h > 0 || m > 0) append("${m}m ")
        append("${s}s")
    }.trim()
}

internal fun formatTimestamp(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.date} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

internal fun formatTime(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
}

@Composable
fun BlockType.displayName(): String = packStringResource(
    when (this) {
        BlockType.TEAMCITY_BUILD -> Res.string.block_type_teamcity_build
        BlockType.GITHUB_ACTION -> Res.string.block_type_github_action
        BlockType.GITHUB_PUBLICATION -> Res.string.block_type_github_publication
        BlockType.SLACK_MESSAGE -> Res.string.block_type_slack_message
    }
)

@Composable
fun Block.typeLabel(): String = when (this) {
    is Block.ActionBlock -> packStringResource(
        when (type) {
            BlockType.TEAMCITY_BUILD -> Res.string.block_label_teamcity_build
            BlockType.GITHUB_ACTION -> Res.string.block_label_github_action
            BlockType.GITHUB_PUBLICATION -> Res.string.block_label_github_publication
            BlockType.SLACK_MESSAGE -> Res.string.block_label_slack_message
        }
    )
    is Block.ContainerBlock -> packStringResource(Res.string.block_label_container)
}

@Composable
fun ReleaseStatus.displayName(): String = packStringResource(
    when (this) {
        ReleaseStatus.PENDING -> Res.string.releases_status_pending
        ReleaseStatus.RUNNING -> Res.string.releases_status_running
        ReleaseStatus.SUCCEEDED -> Res.string.releases_status_succeeded
        ReleaseStatus.FAILED -> Res.string.releases_status_failed
        ReleaseStatus.STOPPED -> Res.string.releases_status_stopped
        ReleaseStatus.CANCELLED -> Res.string.releases_status_cancelled
        ReleaseStatus.ARCHIVED -> Res.string.releases_status_archived
    }
)

@Composable
fun TeamRole.displayName(): String = packStringResource(
    when (this) {
        TeamRole.TEAM_LEAD -> Res.string.teams_role_lead
        TeamRole.COLLABORATOR -> Res.string.teams_role_collaborator
    }
)

@Composable
fun BlockStatus.displayName(): String = packStringResource(
    when (this) {
        BlockStatus.WAITING -> Res.string.block_status_waiting
        BlockStatus.RUNNING -> Res.string.block_status_running
        BlockStatus.SUCCEEDED -> Res.string.block_status_succeeded
        BlockStatus.FAILED -> Res.string.block_status_failed
        BlockStatus.WAITING_FOR_INPUT -> Res.string.block_status_waiting_for_input
        BlockStatus.STOPPED -> Res.string.block_status_stopped
    }
)

@Composable
fun ConnectionType.displayName(): String = packStringResource(
    when (this) {
        ConnectionType.GITHUB -> Res.string.connection_type_github
        ConnectionType.SLACK -> Res.string.connection_type_slack
        ConnectionType.TEAMCITY -> Res.string.connection_type_teamcity
    }
)

@Composable
fun AuditAction.displayName(): String = packStringResource(
    when (this) {
        AuditAction.TEAM_CREATED -> Res.string.audit_action_team_created
        AuditAction.TEAM_UPDATED -> Res.string.audit_action_team_updated
        AuditAction.TEAM_DELETED -> Res.string.audit_action_team_deleted
        AuditAction.MEMBER_JOINED -> Res.string.audit_action_member_joined
        AuditAction.MEMBER_LEFT -> Res.string.audit_action_member_left
        AuditAction.MEMBER_REMOVED -> Res.string.audit_action_member_removed
        AuditAction.MEMBER_ROLE_CHANGED -> Res.string.audit_action_member_role_changed
        AuditAction.INVITE_SENT -> Res.string.audit_action_invite_sent
        AuditAction.INVITE_ACCEPTED -> Res.string.audit_action_invite_accepted
        AuditAction.INVITE_DECLINED -> Res.string.audit_action_invite_declined
        AuditAction.INVITE_CANCELLED -> Res.string.audit_action_invite_cancelled
        AuditAction.JOIN_REQUEST_SUBMITTED -> Res.string.audit_action_join_request_submitted
        AuditAction.JOIN_REQUEST_APPROVED -> Res.string.audit_action_join_request_approved
        AuditAction.JOIN_REQUEST_REJECTED -> Res.string.audit_action_join_request_rejected
        AuditAction.PROJECT_CREATED -> Res.string.audit_action_project_created
        AuditAction.PROJECT_UPDATED -> Res.string.audit_action_project_updated
        AuditAction.PROJECT_DELETED -> Res.string.audit_action_project_deleted
        AuditAction.RELEASE_STARTED -> Res.string.audit_action_release_started
        AuditAction.RELEASE_CANCELLED -> Res.string.audit_action_release_cancelled
        AuditAction.RELEASE_RERUN -> Res.string.audit_action_release_rerun
        AuditAction.RELEASE_ARCHIVED -> Res.string.audit_action_release_archived
        AuditAction.RELEASE_DELETED -> Res.string.audit_action_release_deleted
        AuditAction.RELEASE_STOPPED -> Res.string.audit_action_release_stopped
        AuditAction.RELEASE_RESUMED -> Res.string.audit_action_release_resumed
        AuditAction.BLOCK_RESTARTED -> Res.string.audit_action_block_restarted
        AuditAction.BLOCK_APPROVED -> Res.string.audit_action_block_approved
        AuditAction.BLOCK_STOPPED -> Res.string.audit_action_block_stopped
        AuditAction.CONNECTION_CREATED -> Res.string.audit_action_connection_created
        AuditAction.CONNECTION_UPDATED -> Res.string.audit_action_connection_updated
        AuditAction.CONNECTION_DELETED -> Res.string.audit_action_connection_deleted
        AuditAction.SCHEDULE_CREATED -> Res.string.audit_action_schedule_created
        AuditAction.SCHEDULE_UPDATED -> Res.string.audit_action_schedule_updated
        AuditAction.SCHEDULE_DELETED -> Res.string.audit_action_schedule_deleted
        AuditAction.TRIGGER_CREATED -> Res.string.audit_action_trigger_created
        AuditAction.TRIGGER_UPDATED -> Res.string.audit_action_trigger_updated
        AuditAction.TRIGGER_DELETED -> Res.string.audit_action_trigger_deleted
        AuditAction.TRIGGER_FIRED -> Res.string.audit_action_trigger_fired
        AuditAction.TAG_RENAMED -> Res.string.audit_action_tag_renamed
        AuditAction.TAG_DELETED -> Res.string.audit_action_tag_deleted
        AuditAction.LOCK_FORCE_RELEASED -> Res.string.audit_action_lock_force_released
        AuditAction.USER_LOGIN -> Res.string.audit_action_user_login
        AuditAction.USER_REGISTER -> Res.string.audit_action_user_register
    }
)

@Composable
fun AuditTargetType.displayName(): String = packStringResource(
    when (this) {
        AuditTargetType.TEAM -> Res.string.audit_target_team
        AuditTargetType.USER -> Res.string.audit_target_user
        AuditTargetType.PROJECT -> Res.string.audit_target_project
        AuditTargetType.RELEASE -> Res.string.audit_target_release
        AuditTargetType.BLOCK -> Res.string.audit_target_block
        AuditTargetType.CONNECTION -> Res.string.audit_target_connection
        AuditTargetType.SCHEDULE -> Res.string.audit_target_schedule
        AuditTargetType.TRIGGER -> Res.string.audit_target_trigger
        AuditTargetType.TAG -> Res.string.audit_target_tag
    }
)
