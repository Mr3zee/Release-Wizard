package com.github.mr3zee.releases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import kotlinx.serialization.json.*
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

/**
 * Renders a collapsible file tree from a JSON array of artifact paths.
 */
@Composable
fun ArtifactTreeView(artifactsJson: String) {
    val entries = remember(artifactsJson) {
        try {
            val json = Json.parseToJsonElement(artifactsJson).jsonArray
            json.map { element ->
                when (element) {
                    is JsonPrimitive -> ArtifactEntry(path = element.content)
                    is JsonObject -> ArtifactEntry(
                        path = element["path"]?.jsonPrimitive?.content ?: return@map ArtifactEntry("unknown"),
                        size = element["size"]?.jsonPrimitive?.longOrNull,
                    )
                    else -> ArtifactEntry("unknown")
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    if (entries.isNullOrEmpty()) return

    val tree = remember(entries) { buildArtifactTreeFromEntries(entries) }
    val expandedState = remember(artifactsJson) { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("artifact_tree_section"),
    ) {
        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = packStringResource(Res.string.releases_artifacts),
                style = AppTypography.label,
            )
            Row {
                RwButton(
                    onClick = { expandAll(tree, "", expandedState) },
                    modifier = Modifier.testTag("artifact_expand_all_button"),
                    variant = RwButtonVariant.Ghost,
                ) {
                    Text(packStringResource(Res.string.common_expand_all), style = AppTypography.caption)
                }
                RwButton(
                    onClick = { expandedState.clear() },
                    modifier = Modifier.testTag("artifact_collapse_all_button"),
                    variant = RwButtonVariant.Ghost,
                ) {
                    Text(packStringResource(Res.string.common_collapse_all), style = AppTypography.caption)
                }
            }
        }

        tree.forEach { node ->
            ArtifactNodeRow(node = node, depth = 0, parentPath = "", expandedState = expandedState)
        }
    }
}

@Composable
private fun ArtifactNodeRow(
    node: ArtifactNode,
    depth: Int,
    parentPath: String,
    expandedState: SnapshotStateMap<String, Boolean>,
) {
    val path = if (parentPath.isEmpty()) node.name else "$parentPath/${node.name}"
    val isExpanded = expandedState[path] == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .let {
                if (node.isDirectory) {
                    it.clickable(indication = null, interactionSource = null) { expandedState[path] = !isExpanded }
                } else it
            }
            .padding(vertical = Spacing.xxs)
            .testTag("artifact_node_$path"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isDirectory) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) packStringResource(Res.string.releases_collapse_node, node.name) else packStringResource(Res.string.releases_expand_node, node.name),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = packStringResource(Res.string.releases_folder),
                modifier = Modifier.padding(start = Spacing.xxs).size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "${node.name}/",
                style = AppTypography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(modifier = Modifier.width(Spacing.lg))
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = packStringResource(Res.string.releases_file),
                modifier = Modifier.padding(start = Spacing.xxs).size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = node.name,
                style = AppTypography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.size != null) {
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = formatFileSize(node.size),
                    style = AppTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (node.isDirectory && isExpanded) {
        node.children.forEach { child ->
            ArtifactNodeRow(node = child, depth = depth + 1, parentPath = path, expandedState = expandedState)
        }
    }
}

private fun expandAll(nodes: List<ArtifactNode>, parentPath: String, expandedState: SnapshotStateMap<String, Boolean>) {
    for (node in nodes) {
        if (node.isDirectory) {
            val path = if (parentPath.isEmpty()) node.name else "$parentPath/${node.name}"
            expandedState[path] = true
            expandAll(node.children, path, expandedState)
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        bytes < 1024 -> "$bytes B"
        kb < 1024 -> "${(kb * 10).toLong() / 10.0} KB"
        mb < 1024 -> "${(mb * 10).toLong() / 10.0} MB"
        else -> "${(gb * 10).toLong() / 10.0} GB"
    }
}
