package com.github.mr3zee.releases

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.model.SubBuild
import com.github.mr3zee.model.SubBuildStatus
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import releasewizard.composeapp.generated.resources.*

@Composable
fun SubBuildsSection(
    subBuilds: List<SubBuild>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val succeededCount = subBuilds.count { it.status == SubBuildStatus.SUCCEEDED }
    val totalCount = subBuilds.size

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
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
                    text = packStringResource(Res.string.sub_builds_summary, succeededCount, totalCount),
                    style = AppTypography.subheading,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = Spacing.sm).testTag("sub_builds_list")) {
                    val grouped = subBuilds.groupBy { it.dependencyLevel }.toSortedMap()
                    grouped.entries.forEachIndexed { index, (level, builds) ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = Spacing.xs),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        Text(
                            text = packStringResource(Res.string.sub_builds_stage, level + 1),
                            style = AppTypography.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(bottom = Spacing.xs)
                                .testTag("sub_builds_stage_${level}"),
                        )
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
) {
    Spacer(modifier = Modifier.height(Spacing.sm))
    Text(
        text = packStringResource(Res.string.sub_builds_discovering),
        style = AppTypography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag("sub_builds_discovering"),
    )
}

@Composable
private fun SubBuildRow(
    subBuild: SubBuild,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = subBuild.status.statusIcon(),
            style = AppTypography.body,
            color = subBuild.status.statusColor(),
            modifier = Modifier.testTag("sub_build_status_icon_${subBuild.id}"),
        )
        Text(
            text = subBuild.name,
            style = AppTypography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        subBuild.durationSeconds?.let { seconds ->
            Text(
                text = packStringResource(Res.string.sub_builds_duration, seconds),
                style = AppTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("sub_build_duration_${subBuild.id}"),
            )
        }
    }
}

private fun SubBuildStatus.statusIcon(): String = when (this) {
    SubBuildStatus.QUEUED -> "\u23F2"      // clock / timer
    SubBuildStatus.RUNNING -> "\u25B6"     // play / spinner indicator
    SubBuildStatus.SUCCEEDED -> "\u2713"   // checkmark
    SubBuildStatus.FAILED -> "\u2717"      // X mark
    SubBuildStatus.CANCELLED -> "\u2298"   // circled slash
    SubBuildStatus.UNKNOWN -> "?"
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
