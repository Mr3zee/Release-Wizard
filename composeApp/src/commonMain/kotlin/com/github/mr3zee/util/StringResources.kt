package com.github.mr3zee.util

import androidx.compose.runtime.Composable
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockStatus
import com.github.mr3zee.model.BlockType
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
