package com.github.mr3zee.releases

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.SubBuild
import com.github.mr3zee.model.SubBuildStatus
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.formatDuration
import releasewizard.composeapp.generated.resources.*
import kotlin.time.Duration.Companion.seconds

@Composable
fun SubBuildsSection(
    subBuilds: List<SubBuild>,
    modifier: Modifier = Modifier,
    blockType: BlockType? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val succeededCount = subBuilds.count { it.status == SubBuildStatus.SUCCEEDED }
    val totalCount = subBuilds.size
    val appColors = LocalAppColors.current

    val summaryRes = when (blockType) {
        BlockType.GITHUB_ACTION -> Res.string.gh_jobs_summary
        else -> Res.string.sub_builds_summary
    }

    Surface(
        color = appColors.chromeSurfaceSecondary,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .testTag("sub_builds_section"),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = null) { expanded = !expanded }
                    .testTag("sub_builds_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = packStringResource(summaryRes, succeededCount, totalCount),
                    style = AppTypography.subheading,
                    color = appColors.chromeTextSecondary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = packStringResource(
                        if (expanded) Res.string.common_collapse_all else Res.string.common_expand_all,
                    ),
                    tint = appColors.chromeTextSecondary,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = Spacing.sm).testTag("sub_builds_list")) {
                    val grouped = subBuilds.groupBy { it.dependencyLevel }
                        .entries.sortedBy { it.key }
                        .associate { it.toPair() }
                    val isSingleStage = grouped.size == 1
                    grouped.entries.forEachIndexed { index, (level, builds) ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = Spacing.xs),
                                color = appColors.chromeBorder,
                            )
                        }
                        if (!isSingleStage) {
                            Text(
                                text = packStringResource(Res.string.sub_builds_stage, level + 1),
                                style = AppTypography.label,
                                color = appColors.chromeTextSecondary,
                                modifier = Modifier
                                    .padding(bottom = Spacing.xs)
                                    .testTag("sub_builds_stage_${level}"),
                            )
                        }
                        builds.forEach { subBuild ->
                            SubBuildRow(
                                subBuild = subBuild,
                                modifier = Modifier.testTag("sub_build_row_${subBuild.id}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubBuildsDiscoveringPlaceholder(
    modifier: Modifier = Modifier,
    blockType: BlockType? = null,
) {
    val discoveringRes = when (blockType) {
        BlockType.GITHUB_ACTION -> Res.string.gh_jobs_discovering
        else -> Res.string.sub_builds_discovering
    }
    val appColors = LocalAppColors.current
    Spacer(modifier = Modifier.height(Spacing.sm))
    Text(
        text = packStringResource(discoveringRes),
        style = AppTypography.bodySmall,
        color = appColors.chromeTextSecondary,
        modifier = modifier.testTag("sub_builds_discovering"),
    )
}

@Composable
private fun SubBuildRow(
    subBuild: SubBuild,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = subBuild.status.statusIcon(),
            contentDescription = subBuild.status.name.lowercase().replaceFirstChar { it.uppercase() },
            tint = subBuild.status.statusColor(),
            modifier = Modifier.size(16.dp).testTag("sub_build_status_icon_${subBuild.id}"),
        )
        Text(
            text = subBuild.name,
            style = AppTypography.bodySmall,
            color = appColors.chromeTextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        subBuild.durationSeconds?.let { durationSec ->
            Text(
                text = formatDuration(durationSec.seconds),
                style = AppTypography.caption,
                color = appColors.chromeTextTertiary,
                modifier = Modifier.testTag("sub_build_duration_${subBuild.id}"),
            )
        }
        subBuild.buildUrl?.let { url ->
            val isSafeUrl = url.startsWith("https://") || url.startsWith("http://")
            if (isSafeUrl) {
                Text(
                    text = packStringResource(Res.string.sub_builds_open_link),
                    style = AppTypography.label,
                    color = appColors.buttonPrimaryBg,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                        ) { uriHandler.openUri(url) }
                        .testTag("sub_build_link_${subBuild.id}"),
                )
            }
        }
    }
}

private fun SubBuildStatus.statusIcon(): ImageVector = when (this) {
    SubBuildStatus.QUEUED -> Icons.Default.Schedule
    SubBuildStatus.RUNNING -> Icons.Default.PlayArrow
    SubBuildStatus.SUCCEEDED -> Icons.Default.Check
    SubBuildStatus.FAILED -> Icons.Default.Close
    SubBuildStatus.CANCELLED -> Icons.Default.Block
    SubBuildStatus.UNKNOWN -> Icons.Default.Help
}

@Composable
private fun SubBuildStatus.statusColor(): Color {
    val appColors = LocalAppColors.current
    return when (this) {
        SubBuildStatus.QUEUED -> appColors.blockStatusWaiting
        SubBuildStatus.RUNNING -> appColors.blockStatusRunning
        SubBuildStatus.SUCCEEDED -> appColors.blockStatusSucceeded
        SubBuildStatus.FAILED -> appColors.blockStatusFailed
        SubBuildStatus.CANCELLED -> appColors.statusCancelled
        SubBuildStatus.UNKNOWN -> appColors.blockStatusWaiting
    }
}
