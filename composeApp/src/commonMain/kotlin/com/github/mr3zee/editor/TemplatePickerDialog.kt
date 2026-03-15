package com.github.mr3zee.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.Parameter

@Composable
fun TemplatePickerDialog(
    parameters: List<Parameter>,
    predecessors: List<Block>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Template") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .testTag("template_picker_list"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (parameters.isNotEmpty()) {
                    item {
                        Text(
                            "Parameters",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(parameters, key = { it.key }) { param ->
                        val expr = $$"${param.$${param.key}}"
                        TemplateItem(
                            label = param.key,
                            expression = expr,
                            description = if (param.value.isNotEmpty()) "Default: ${param.value}" else null,
                            onClick = { onSelect(expr) },
                        )
                    }
                }
                val actionPredecessors = predecessors.filterIsInstance<Block.ActionBlock>()
                    .filter { it.outputs.isNotEmpty() }
                if (actionPredecessors.isNotEmpty()) {
                    item {
                        Text(
                            "Block Outputs",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    actionPredecessors.forEach { block ->
                        items(block.outputs, key = { output -> "${block.id.value}/$output" }) { output ->
                            val expr = $$"${block.$${block.id.value}.$$output}"
                            TemplateItem(
                                label = "${block.name} / $output",
                                expression = expr,
                                onClick = { onSelect(expr) },
                            )
                        }
                    }
                }
                if (parameters.isEmpty() && actionPredecessors.isEmpty()) {
                    item {
                        Text(
                            "No parameters or predecessor outputs available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("template_item_$label"),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                expression,
                style = MaterialTheme.typography.bodySmall,
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
