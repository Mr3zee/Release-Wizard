package com.github.mr3zee.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.displayName
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@Composable
fun EditorToolbar(
    onAddBlock: (BlockType, String) -> Unit,
    onAddContainer: (String) -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    hasSelection: Boolean,
    hasClipboard: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = modifier
            .width(200.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            packStringResource(Res.string.editor_toolbar_blocks),
            style = AppTypography.subheading,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )

        BlockType.entries.forEach { type ->
            RwButton(
                onClick = { onAddBlock(type, defaultBlockName(type)) },
                variant = RwButtonVariant.Secondary,
                enabled = enabled,
                contentColor = blockTypeColor(type, appColors),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_block_${type.name}"),
            ) {
                Text(
                    type.displayName(),
                    style = AppTypography.label,
                )
            }
        }

        RwButton(
            onClick = { onAddContainer("Container") },
            variant = RwButtonVariant.Secondary,
            enabled = enabled,
            contentColor = appColors.containerBlock,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_container"),
        ) {
            Text(packStringResource(Res.string.editor_toolbar_container), style = AppTypography.label)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

        Text(
            packStringResource(Res.string.editor_toolbar_actions),
            style = AppTypography.subheading,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            RwButton(
                onClick = onUndo,
                variant = RwButtonVariant.Secondary,
                enabled = enabled && canUndo,
                modifier = Modifier.weight(1f).testTag("undo_button"),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 6.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(packStringResource(Res.string.editor_toolbar_undo))
            }
            RwButton(
                onClick = onRedo,
                variant = RwButtonVariant.Secondary,
                enabled = enabled && canRedo,
                modifier = Modifier.weight(1f).testTag("redo_button"),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 6.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(packStringResource(Res.string.editor_toolbar_redo))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            RwButton(
                onClick = onCopy,
                variant = RwButtonVariant.Secondary,
                enabled = hasSelection, // Copy always allowed
                modifier = Modifier.weight(1f).testTag("copy_button"),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 6.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(packStringResource(Res.string.editor_toolbar_copy))
            }
            RwButton(
                onClick = onPaste,
                variant = RwButtonVariant.Secondary,
                enabled = enabled && hasClipboard,
                modifier = Modifier.weight(1f).testTag("paste_button"),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 6.dp),
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(packStringResource(Res.string.editor_toolbar_paste))
            }
        }

        RwButton(
            onClick = onDelete,
            variant = RwButtonVariant.Secondary,
            enabled = enabled && hasSelection,
            contentColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().testTag("delete_button"),
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 6.dp),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(packStringResource(Res.string.editor_toolbar_delete))
        }
    }
}

private fun defaultBlockName(type: BlockType): String = when (type) {
    BlockType.TEAMCITY_BUILD -> "Build"
    BlockType.GITHUB_ACTION -> "Action"
    BlockType.GITHUB_PUBLICATION -> "Publish"
    BlockType.MAVEN_CENTRAL_PUBLICATION -> "Maven Central"
    BlockType.SLACK_MESSAGE -> "Notify"
}

// Uses blockTypeColor from DagCanvas.kt
