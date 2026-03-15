package com.github.mr3zee.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@Composable
fun TemplatePickerDialog(
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packStringResource(Res.string.editor_template_picker_title)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .testTag("template_picker_list"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (paramSuggestions.isNotEmpty()) {
                    item {
                        Text(
                            packStringResource(Res.string.editor_template_parameters),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(paramSuggestions, key = { it.label }) { suggestion ->
                        TemplateItem(
                            label = suggestion.label,
                            expression = suggestion.insertText,
                            description = suggestion.description,
                            onClick = { onSelect(suggestion.insertText) },
                        )
                    }
                }
                if (outputSuggestions.isNotEmpty()) {
                    item {
                        Text(
                            packStringResource(Res.string.editor_template_block_outputs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(outputSuggestions, key = { it.insertText }) { suggestion ->
                        TemplateItem(
                            label = suggestion.label,
                            expression = suggestion.insertText,
                            onClick = { onSelect(suggestion.insertText) },
                        )
                    }
                }
                if (suggestions.isEmpty()) {
                    item {
                        Text(
                            packStringResource(Res.string.editor_template_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(packStringResource(Res.string.common_cancel))
            }
        },
    )
}

@Composable
private fun TemplateItem(
    label: String,
    expression: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("template_item_$label"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                expression,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
