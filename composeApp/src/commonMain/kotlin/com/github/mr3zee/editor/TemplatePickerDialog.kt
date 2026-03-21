package com.github.mr3zee.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

/**
 * Template picker shown as a DropdownMenu anchored to the template button.
 * Uses Column + verticalScroll (no LazyColumn — project constraint).
 */
@Composable
fun TemplatePickerDropdown(
    expanded: Boolean,
    parameters: List<Parameter>,
    predecessors: List<Block>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultValueMarker = "\u0000"
    val defaultValueTemplate = packStringResource(Res.string.editor_template_default_value, defaultValueMarker)
    val suggestions = remember(parameters, predecessors, defaultValueTemplate) {
        buildSuggestions(parameters, predecessors) { value ->
            defaultValueTemplate.replace(defaultValueMarker, value)
        }
    }
    val paramSuggestions = remember(suggestions) {
        suggestions.filter { it.category == SuggestionCategory.PARAMETER }
    }
    val outputSuggestions = remember(suggestions) {
        suggestions.filter { it.category == SuggestionCategory.BLOCK_OUTPUT }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("template_picker_dropdown"),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
                .widthIn(min = 250.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (paramSuggestions.isNotEmpty()) {
                Text(
                    packStringResource(Res.string.editor_template_parameters),
                    style = AppTypography.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
                paramSuggestions.forEach { suggestion ->
                    TemplateDropdownItem(
                        label = suggestion.label,
                        expression = suggestion.insertText,
                        description = suggestion.description,
                        onClick = { onSelect(suggestion.insertText) },
                    )
                }
            }
            if (outputSuggestions.isNotEmpty()) {
                Text(
                    packStringResource(Res.string.editor_template_block_outputs),
                    style = AppTypography.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm, end = Spacing.sm, bottom = Spacing.xs),
                )
                outputSuggestions.forEach { suggestion ->
                    TemplateDropdownItem(
                        label = suggestion.label,
                        expression = suggestion.insertText,
                        onClick = { onSelect(suggestion.insertText) },
                    )
                }
            }
            if (suggestions.isEmpty()) {
                Text(
                    packStringResource(Res.string.editor_template_empty),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun TemplateDropdownItem(
    label: String,
    expression: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                Text(label, style = AppTypography.body)
                Text(
                    expression,
                    style = AppTypography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (description != null) {
                    Text(
                        description,
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = onClick,
        modifier = Modifier.testTag("template_item_$label")
            .pointerHoverIcon(PointerIcon.Hand),
    )
}
