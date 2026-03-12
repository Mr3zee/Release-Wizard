package com.github.mr3zee.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.BlockType

@Composable
fun EditorToolbar(
    onAddBlock: (BlockType, String) -> Unit,
    onAddContainer: (String) -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    hasSelection: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Blocks",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        BlockType.entries.forEach { type ->
            OutlinedButton(
                onClick = { onAddBlock(type, defaultBlockName(type)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_block_${type.name}"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = blockTypeColor(type),
                ),
            ) {
                Text(
                    blockTypeDisplayName(type),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        OutlinedButton(
            onClick = { onAddContainer("Container") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_container"),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF6B7280),
            ),
        ) {
            Text("Container", style = MaterialTheme.typography.labelMedium)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            "Actions",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.weight(1f).testTag("undo_button"),
            ) {
                Text("Undo")
            }
            OutlinedButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.weight(1f).testTag("redo_button"),
            ) {
                Text("Redo")
            }
        }

        OutlinedButton(
            onClick = onDelete,
            enabled = hasSelection,
            modifier = Modifier.fillMaxWidth().testTag("delete_button"),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Delete")
        }
    }
}

private fun blockTypeDisplayName(type: BlockType): String = when (type) {
    BlockType.TEAMCITY_BUILD -> "TeamCity Build"
    BlockType.GITHUB_ACTION -> "GitHub Action"
    BlockType.GITHUB_PUBLICATION -> "GitHub Publication"
    BlockType.MAVEN_CENTRAL_PUBLICATION -> "Maven Central"
    BlockType.SLACK_MESSAGE -> "Slack Message"
    BlockType.USER_ACTION -> "User Action"
}

private fun defaultBlockName(type: BlockType): String = when (type) {
    BlockType.TEAMCITY_BUILD -> "Build"
    BlockType.GITHUB_ACTION -> "Action"
    BlockType.GITHUB_PUBLICATION -> "Publish"
    BlockType.MAVEN_CENTRAL_PUBLICATION -> "Maven Central"
    BlockType.SLACK_MESSAGE -> "Notify"
    BlockType.USER_ACTION -> "Approve"
}

// Uses blockTypeColor from DagCanvas.kt
