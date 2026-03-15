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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

/**
 * Renders a collapsible file tree from a JSON array of artifact paths.
 */
@Composable
fun ArtifactTreeView(artifactsJson: String) {
    val paths = remember(artifactsJson) {
        try {
            Json.decodeFromString<List<String>>(artifactsJson)
        } catch (_: Exception) {
            null
        }
    }

    if (paths.isNullOrEmpty()) return

    val tree = remember(paths) { buildArtifactTree(paths) }
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("artifact_tree_section"),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = packStringResource(Res.string.releases_artifacts),
                style = MaterialTheme.typography.labelMedium,
            )
            Row {
                TextButton(
                    onClick = { expandAll(tree, "", expandedState) },
                    modifier = Modifier.testTag("artifact_expand_all_button"),
                ) {
                    Text(packStringResource(Res.string.common_expand_all), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = { expandedState.clear() },
                    modifier = Modifier.testTag("artifact_collapse_all_button"),
                ) {
                    Text(packStringResource(Res.string.common_collapse_all), style = MaterialTheme.typography.labelSmall)
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
                    it.clickable { expandedState[path] = !isExpanded }
                } else it
            }
            .padding(vertical = 2.dp)
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
                modifier = Modifier.size(16.dp).padding(start = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${node.name}/",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = packStringResource(Res.string.releases_file),
                modifier = Modifier.size(16.dp).padding(start = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
