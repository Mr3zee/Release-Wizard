package com.github.mr3zee.releases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json

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

    if (paths == null || paths.isEmpty()) return

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
                text = "Artifacts",
                style = MaterialTheme.typography.labelMedium,
            )
            Row {
                TextButton(
                    onClick = { expandAll(tree, "", expandedState) },
                    modifier = Modifier.testTag("artifact_expand_all_button"),
                ) {
                    Text("Expand All", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = { expandedState.clear() },
                    modifier = Modifier.testTag("artifact_collapse_all_button"),
                ) {
                    Text("Collapse All", style = MaterialTheme.typography.labelSmall)
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
            val chevron = if (isExpanded) "v " else "> "
            Text(
                text = "$chevron${node.name}/",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
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
