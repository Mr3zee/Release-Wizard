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
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCheckbox
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.model.*
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.displayName
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

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
            .padding(Spacing.md),
    ) {
        Text(
            packStringResource(Res.string.editor_prop_title),
            style = AppTypography.subheading,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )

        if (block == null) {
            Text(
                packStringResource(Res.string.editor_prop_empty_hint),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // Name
        var name by remember(block.id) { mutableStateOf(block.name) }
        RwTextField(
            value = name,
            onValueChange = {
                name = it
                onUpdateName(block.id, it)
            },
            label = packStringResource(Res.string.editor_prop_name),
            placeholder = packStringResource(Res.string.editor_prop_name),
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("block_name_field"),
        )

        Spacer(Modifier.height(Spacing.md))

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
                    packStringResource(Res.string.editor_prop_container_type),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    packPluralStringResource(Res.plurals.child_blocks, block.children.blocks.size, block.children.blocks.size),
                    style = AppTypography.bodySmall,
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
    Text(packStringResource(Res.string.editor_prop_type), style = AppTypography.label)
    Box {
        RwButton(
            onClick = { typeExpanded = true },
            variant = RwButtonVariant.Secondary,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("block_type_selector"),
        ) {
            Text(block.type.displayName())
        }
        DropdownMenu(
            expanded = typeExpanded,
            onDismissRequest = { typeExpanded = false },
        ) {
            BlockType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName()) },
                    onClick = {
                        onUpdateType(block.id, type)
                        typeExpanded = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(Spacing.md))

    // Timeout
    var timeoutText by remember(block.id) {
        mutableStateOf(block.timeoutSeconds?.toString() ?: "")
    }
    RwTextField(
        value = timeoutText,
        onValueChange = { text ->
            timeoutText = text
            val seconds = text.toLongOrNull()
            onUpdateTimeout(block.id, seconds)
        },
        label = packStringResource(Res.string.editor_prop_timeout),
        placeholder = packStringResource(Res.string.editor_prop_timeout),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("block_timeout_field"),
    )

    Spacer(Modifier.height(Spacing.md))

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

    Spacer(Modifier.height(Spacing.md))

    // Parameters
    Text(packStringResource(Res.string.editor_prop_parameters), style = AppTypography.label)
    Spacer(Modifier.height(Spacing.xs))

    var params by remember(block.id) { mutableStateOf(block.parameters) }
    if (params != block.parameters) params = block.parameters

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
        Spacer(Modifier.height(Spacing.xs))
        }
    }

    RwButton(
        onClick = {
            params = params + Parameter(key = "", value = "")
            onUpdateParameters(block.id, params)
        },
        variant = RwButtonVariant.Secondary,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("add_parameter_button"),
    ) {
        Text(packStringResource(Res.string.editor_prop_add_parameter))
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
    val gateHeader = if (gateCount > 0) {
        packStringResource(Res.string.editor_gate_section_header_count, gateCount)
    } else {
        packStringResource(Res.string.editor_gate_section_header)
    }
    val label = gateHeader + if (expanded) " \u25BE" else " \u25B8"

    RwButton(
        onClick = { expanded = !expanded },
        variant = RwButtonVariant.Secondary,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("gate_section_toggle"),
    ) {
        Text(label, style = AppTypography.label)
    }

    if (expanded) {
        Spacer(Modifier.height(Spacing.sm))
        Column(modifier = Modifier.testTag("gate_section_content")) {
            SingleGateEditor(
                label = packStringResource(Res.string.editor_gate_pre_label),
                gate = block.preGate,
                blockId = block.id,
                projectParameters = projectParameters,
                predecessors = predecessors,
                onUpdate = { onUpdatePreGate(block.id, it) },
                enabled = enabled,
                testTagPrefix = "pre_gate",
            )

            Spacer(Modifier.height(Spacing.sm))
            SingleGateEditor(
                label = packStringResource(Res.string.editor_gate_post_label),
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

@OptIn(ExperimentalMaterial3Api::class)
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
        RwCheckbox(
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
        Text(label, style = AppTypography.label)
    }

    if (isEnabled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TemplateAutocompleteField(
                value = message,
                onValueChange = { text ->
                    message = text
                    onUpdate(gate.copy(message = text))
                },
                projectParameters = projectParameters,
                predecessors = predecessors,
                label = { Text(packStringResource(Res.string.editor_gate_message)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                textStyle = AppTypography.bodySmall,
                testTag = "${testTagPrefix}_message_field",
            )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(packStringResource(Res.string.editor_template_tooltip)) } },
                state = rememberTooltipState(),
            ) {
                RwButton(
                    onClick = { showTemplatePicker = true },
                    variant = RwButtonVariant.Ghost,
                    enabled = enabled,
                    contentPadding = PaddingValues(Spacing.xs),
                    modifier = Modifier.testTag("${testTagPrefix}_template_button"),
                ) {
                    Text(packStringResource(Res.string.editor_template_button), style = AppTypography.bodySmall)
                }
            }
}

        val countValue = requiredCount.toIntOrNull()
        val isCountError = countValue == null || countValue < 1
        RwTextField(
            value = requiredCount,
            onValueChange = { text ->
                requiredCount = text
                val count = text.toIntOrNull()?.coerceAtLeast(1) ?: 1
                onUpdate(gate.copy(approvalRule = gate.approvalRule.copy(requiredCount = count)))
            },
            label = packStringResource(Res.string.editor_gate_required_approvals),
            placeholder = packStringResource(Res.string.editor_gate_required_approvals),
            supportingText = if (isCountError) {{ Text(packStringResource(Res.string.editor_gate_approval_count_error)) }} else null,
            isError = isCountError,
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_count_field"),
            textStyle = AppTypography.bodySmall,
        )
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            parameters = projectParameters,
            predecessors = predecessors,
            onSelect = { expr ->
                message = insertExpressionSafely(message, expr)
                val currentGate = gate ?: Gate()
                onUpdate(currentGate.copy(message = message))
                showTemplatePicker = false
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterRow(
    parameter: Parameter,
    projectParameters: List<Parameter>,
    predecessors: List<Block>,
    onUpdate: (Parameter) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean = true,
) {
    var showTemplatePicker by remember(parameter.key) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RwTextField(
            value = parameter.key,
            onValueChange = { onUpdate(parameter.copy(key = it)) },
            placeholder = packStringResource(Res.string.editor_prop_key),
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            textStyle = AppTypography.bodySmall,
        )
        TemplateAutocompleteField(
            value = parameter.value,
            onValueChange = { onUpdate(parameter.copy(value = it)) },
            projectParameters = projectParameters,
            predecessors = predecessors,
            label = { Text(packStringResource(Res.string.editor_prop_value)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            textStyle = AppTypography.bodySmall,
            testTag = "param_value_field",
        )
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(packStringResource(Res.string.editor_template_tooltip)) } },
            state = rememberTooltipState(),
        ) {
            RwButton(
                onClick = { showTemplatePicker = true },
                variant = RwButtonVariant.Ghost,
                enabled = enabled,
                contentPadding = PaddingValues(Spacing.xs),
                modifier = Modifier.testTag("insert_template_button"),
            ) {
                Text(packStringResource(Res.string.editor_template_button), style = AppTypography.bodySmall)
            }
        }
        RwButton(
            onClick = onRemove,
            variant = RwButtonVariant.Ghost,
            enabled = enabled,
            contentPadding = PaddingValues(Spacing.xs),
            contentColor = MaterialTheme.colorScheme.error,
        ) {
            Text(packStringResource(Res.string.editor_prop_remove))
        }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            parameters = projectParameters,
            predecessors = predecessors,
            onSelect = { expr ->
                onUpdate(parameter.copy(value = insertExpressionSafely(parameter.value, expr)))
                showTemplatePicker = false
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}
