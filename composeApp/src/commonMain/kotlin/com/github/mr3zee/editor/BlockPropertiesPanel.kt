package com.github.mr3zee.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.*

@Composable
fun BlockPropertiesPanel(
    block: Block?,
    onUpdateName: (BlockId, String) -> Unit,
    onUpdateType: (BlockId, BlockType) -> Unit,
    onUpdateParameters: (BlockId, List<Parameter>) -> Unit,
    onUpdateTimeout: (BlockId, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(260.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            "Properties",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (block == null) {
            Text(
                "Select a block to edit its properties",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // Name
        var name by remember(block.id) { mutableStateOf(block.name) }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onUpdateName(block.id, it)
            },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("block_name_field"),
        )

        Spacer(Modifier.height(12.dp))

        when (block) {
            is Block.ActionBlock -> {
                ActionBlockProperties(
                    block = block,
                    onUpdateType = onUpdateType,
                    onUpdateParameters = onUpdateParameters,
                    onUpdateTimeout = onUpdateTimeout,
                )
            }
            is Block.ContainerBlock -> {
                Text(
                    "Container block",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${block.children.blocks.size} child blocks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionBlockProperties(
    block: Block.ActionBlock,
    onUpdateType: (BlockId, BlockType) -> Unit,
    onUpdateParameters: (BlockId, List<Parameter>) -> Unit,
    onUpdateTimeout: (BlockId, Long?) -> Unit,
) {
    // Type selector
    var typeExpanded by remember { mutableStateOf(false) }
    Text("Type", style = MaterialTheme.typography.labelMedium)
    Box {
        OutlinedButton(
            onClick = { typeExpanded = true },
            modifier = Modifier.fillMaxWidth().testTag("block_type_selector"),
        ) {
            Text(block.type.name.lowercase().replace("_", " "))
        }
        DropdownMenu(
            expanded = typeExpanded,
            onDismissRequest = { typeExpanded = false },
        ) {
            BlockType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase().replace("_", " ")) },
                    onClick = {
                        onUpdateType(block.id, type)
                        typeExpanded = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // Timeout
    var timeoutText by remember(block.id) {
        mutableStateOf(block.timeoutSeconds?.toString() ?: "")
    }
    OutlinedTextField(
        value = timeoutText,
        onValueChange = { text ->
            timeoutText = text
            val seconds = text.toLongOrNull()
            onUpdateTimeout(block.id, seconds)
        },
        label = { Text("Timeout (seconds)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("block_timeout_field"),
    )

    Spacer(Modifier.height(12.dp))

    // Parameters
    Text("Parameters", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))

    var params by remember(block.id) { mutableStateOf(block.parameters) }

    params.forEachIndexed { index, param ->
        ParameterRow(
            parameter = param,
            onUpdate = { updated ->
                params = params.toMutableList().apply { set(index, updated) }
                onUpdateParameters(block.id, params)
            },
            onRemove = {
                params = params.toMutableList().apply { removeAt(index) }
                onUpdateParameters(block.id, params)
            },
        )
        Spacer(Modifier.height(4.dp))
    }

    OutlinedButton(
        onClick = {
            params = params + Parameter(key = "", value = "")
            onUpdateParameters(block.id, params)
        },
        modifier = Modifier.fillMaxWidth().testTag("add_parameter_button"),
    ) {
        Text("+ Add Parameter")
    }
}

@Composable
private fun ParameterRow(
    parameter: Parameter,
    onUpdate: (Parameter) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = parameter.key,
            onValueChange = { onUpdate(parameter.copy(key = it)) },
            label = { Text("Key") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = parameter.value,
            onValueChange = { onUpdate(parameter.copy(value = it)) },
            label = { Text("Value") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = onRemove,
            contentPadding = PaddingValues(4.dp),
        ) {
            Text("x", color = MaterialTheme.colorScheme.error)
        }
    }
}
