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
    graph: DagGraph,
    projectParameters: List<Parameter>,
    onUpdateName: (BlockId, String) -> Unit,
    onUpdateType: (BlockId, BlockType) -> Unit,
    onUpdateParameters: (BlockId, List<Parameter>) -> Unit,
    onUpdateTimeout: (BlockId, Long?) -> Unit,
    onUpdatePreGate: (BlockId, Gate?) -> Unit,
    onUpdatePostGate: (BlockId, Gate?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("block_name_field"),
        )

        Spacer(Modifier.height(12.dp))

        when (block) {
            is Block.ActionBlock -> {
                ActionBlockProperties(
                    block = block,
                    graph = graph,
                    projectParameters = projectParameters,
                    onUpdateType = onUpdateType,
                    onUpdateParameters = onUpdateParameters,
                    onUpdateTimeout = onUpdateTimeout,
                    onUpdatePreGate = onUpdatePreGate,
                    onUpdatePostGate = onUpdatePostGate,
                    enabled = enabled,
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
    graph: DagGraph,
    projectParameters: List<Parameter>,
    onUpdateType: (BlockId, BlockType) -> Unit,
    onUpdateParameters: (BlockId, List<Parameter>) -> Unit,
    onUpdateTimeout: (BlockId, Long?) -> Unit,
    onUpdatePreGate: (BlockId, Gate?) -> Unit,
    onUpdatePostGate: (BlockId, Gate?) -> Unit,
    enabled: Boolean = true,
) {
    // Type selector
    var typeExpanded by remember(block.id) { mutableStateOf(false) }
    Text("Type", style = MaterialTheme.typography.labelMedium)
    Box {
        OutlinedButton(
            onClick = { typeExpanded = true },
            enabled = enabled,
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
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("block_timeout_field"),
    )

    Spacer(Modifier.height(12.dp))

    // Compute predecessors for template picker (shared by gates and parameters)
    val predecessors = remember(graph, block.id) {
        com.github.mr3zee.dag.findPredecessors(graph, block.id)
    }

    // Approval Gates
    GateConfigSection(
        block = block,
        projectParameters = projectParameters,
        predecessors = predecessors,
        onUpdatePreGate = onUpdatePreGate,
        onUpdatePostGate = onUpdatePostGate,
        enabled = enabled,
    )

    Spacer(Modifier.height(12.dp))

    // Parameters
    Text("Parameters", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))

    var params by remember(block.id, block.parameters) { mutableStateOf(block.parameters) }

    params.forEachIndexed { index, param ->
        key(block.id, index) {
        ParameterRow(
            parameter = param,
            projectParameters = projectParameters,
            predecessors = predecessors,
            onUpdate = { updated ->
                params = params.toMutableList().apply { set(index, updated) }
                onUpdateParameters(block.id, params)
            },
            onRemove = {
                params = params.toMutableList().apply { removeAt(index) }
                onUpdateParameters(block.id, params)
            },
            enabled = enabled,
        )
        Spacer(Modifier.height(4.dp))
        }
    }

    OutlinedButton(
        onClick = {
            params = params + Parameter(key = "", value = "")
            onUpdateParameters(block.id, params)
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("add_parameter_button"),
    ) {
        Text("+ Add Parameter")
    }
}

@Composable
private fun GateConfigSection(
    block: Block.ActionBlock,
    projectParameters: List<Parameter>,
    predecessors: List<Block>,
    onUpdatePreGate: (BlockId, Gate?) -> Unit,
    onUpdatePostGate: (BlockId, Gate?) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember(block.id) { mutableStateOf(false) }
    val gateCount = listOfNotNull(block.preGate, block.postGate).size
    val label = buildString {
        append("Approval Gates")
        if (gateCount > 0) append(" ($gateCount)")
        append(if (expanded) " \u25BE" else " \u25B8")
    }

    OutlinedButton(
        onClick = { expanded = !expanded },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("gate_section_toggle"),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }

    if (expanded) {
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.testTag("gate_section_content")) {
            SingleGateEditor(
                label = "Pre-gate",
                gate = block.preGate,
                blockId = block.id,
                projectParameters = projectParameters,
                predecessors = predecessors,
                onUpdate = { onUpdatePreGate(block.id, it) },
                enabled = enabled,
                testTagPrefix = "pre_gate",
            )

            Spacer(Modifier.height(8.dp))
            SingleGateEditor(
                label = "Post-gate",
                gate = block.postGate,
                blockId = block.id,
                projectParameters = projectParameters,
                predecessors = predecessors,
                onUpdate = { onUpdatePostGate(block.id, it) },
                enabled = enabled,
                testTagPrefix = "post_gate",
            )
        }
    }
}

@Composable
private fun SingleGateEditor(
    label: String,
    gate: Gate?,
    blockId: BlockId,
    projectParameters: List<Parameter>,
    predecessors: List<Block>,
    onUpdate: (Gate?) -> Unit,
    enabled: Boolean,
    testTagPrefix: String,
) {
    val isEnabled = gate != null
    var message by remember(blockId, isEnabled) { mutableStateOf(gate?.message ?: "") }
    var requiredCount by remember(blockId, isEnabled) { mutableStateOf(gate?.approvalRule?.requiredCount?.toString() ?: "1") }
    var showTemplatePicker by remember(blockId) { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = isEnabled,
            onCheckedChange = { checked ->
                if (checked) {
                    onUpdate(Gate())
                } else {
                    onUpdate(null)
                }
            },
            enabled = enabled,
            modifier = Modifier.testTag("${testTagPrefix}_checkbox"),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }

    if (isEnabled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { text ->
                    message = text
                    onUpdate(gate.copy(message = text))
                },
                label = { Text("Message") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.weight(1f).testTag("${testTagPrefix}_message_field"),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { showTemplatePicker = true },
                enabled = enabled,
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.testTag("${testTagPrefix}_template_button"),
            ) {
                Text("{}", style = MaterialTheme.typography.bodySmall)
            }
        }

        val countValue = requiredCount.toIntOrNull()
        val isCountError = countValue == null || countValue < 1
        OutlinedTextField(
            value = requiredCount,
            onValueChange = { text ->
                requiredCount = text
                val count = text.toIntOrNull()?.coerceAtLeast(1) ?: 1
                onUpdate(gate.copy(approvalRule = gate.approvalRule.copy(requiredCount = count)))
            },
            label = { Text("Required approvals") },
            supportingText = if (isCountError) {{ Text("Must be 1 or more") }} else null,
            isError = isCountError,
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_count_field"),
            textStyle = MaterialTheme.typography.bodySmall,
        )
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            parameters = projectParameters,
            predecessors = predecessors,
            onSelect = { expr ->
                message += expr
                val currentGate = gate ?: Gate()
                onUpdate(currentGate.copy(message = message))
                showTemplatePicker = false
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}

@Composable
private fun ParameterRow(
    parameter: Parameter,
    projectParameters: List<Parameter>,
    predecessors: List<Block>,
    onUpdate: (Parameter) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean = true,
) {
    var showTemplatePicker by remember { mutableStateOf(false) }

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
            enabled = enabled,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = parameter.value,
            onValueChange = { onUpdate(parameter.copy(value = it)) },
            label = { Text("Value") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = { showTemplatePicker = true },
            enabled = enabled,
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.testTag("insert_template_button"),
        ) {
            Text("{}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(
            onClick = onRemove,
            enabled = enabled,
            contentPadding = PaddingValues(4.dp),
        ) {
            Text("x", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            parameters = projectParameters,
            predecessors = predecessors,
            onSelect = { expr ->
                onUpdate(parameter.copy(value = parameter.value + expr))
                showTemplatePicker = false
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}
