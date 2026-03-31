package com.github.mr3zee.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.ExternalConfig
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwDropdownMenu
import com.github.mr3zee.components.RwDropdownMenuItem
import com.github.mr3zee.components.RwCheckbox
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwMarkdownField
import com.github.mr3zee.components.RwSegmentedTabRow
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.model.*
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
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
    connections: List<Connection> = emptyList(),
    externalConfigs: List<ExternalConfig> = emptyList(),
    isFetchingConfigs: Boolean = false,
    configFetchError: String? = null,
    isFetchingConfigParams: Boolean = false,
    onUpdateName: (BlockId, String) -> Unit,
    onUpdateBlockId: (BlockId, BlockId) -> Boolean = { _, _ -> false },
    onUpdateType: (BlockId, BlockType) -> Unit,
    onUpdateConnectionId: (BlockId, ConnectionId?) -> Unit = { _, _ -> },
    onSelectConfig: (BlockId, String) -> Unit = { _, _ -> },
    onRefreshConfigs: (BlockId) -> Unit = {},
    onRefreshConfigParams: (BlockId) -> Unit = {},
    onUpdateParameters: (BlockId, List<Parameter>) -> Unit,
    onUpdateTimeout: (BlockId, Long?) -> Unit,
    onUpdatePreGate: (BlockId, Gate?) -> Unit,
    onUpdatePostGate: (BlockId, Gate?) -> Unit,
    onUpdateDescription: (BlockId, String) -> Unit = { _, _ -> },
    onUpdateInjectWebhookUrl: (BlockId, Boolean) -> Unit = { _, _ -> },
    projectDescription: String = "",
    onUpdateProjectDescription: (String) -> Unit = {},
    onUpdateProjectParameters: (List<Parameter>) -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxHeight(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(Spacing.md),
    ) {
        if (block == null) {
            // Project description editor when no block is selected
            Text(
                packStringResource(Res.string.editor_project_description_header),
                style = AppTypography.subheading,
            )
            Spacer(Modifier.height(Spacing.xs))
            var projDesc by remember(projectDescription) { mutableStateOf(projectDescription) }
            RwMarkdownField(
                value = projDesc,
                onValueChange = {
                    projDesc = it
                    onUpdateProjectDescription(it)
                },
                placeholder = packStringResource(Res.string.projects_project_description_placeholder),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("project_description_field"),
                testTag = "project_description_field",
            )

            Spacer(Modifier.height(Spacing.lg))

            // Project parameters editor
            Text(
                packStringResource(Res.string.editor_project_parameters_header),
                style = AppTypography.subheading,
            )
            Spacer(Modifier.height(Spacing.sm))

            var projParams by remember(projectParameters) { mutableStateOf(projectParameters) }
            if (projParams != projectParameters) projParams = projectParameters

            projParams.forEachIndexed { index, param ->
                key(index) {
                    SimpleParameterCard(
                        parameter = param,
                        keyPlaceholder = packStringResource(Res.string.editor_project_param_key),
                        valuePlaceholder = packStringResource(Res.string.editor_project_param_value),
                        onUpdate = { updated ->
                            projParams = projParams.toMutableList().apply { set(index, updated) }
                            onUpdateProjectParameters(projParams)
                        },
                        onRemove = {
                            projParams = projParams.toMutableList().apply { removeAt(index) }
                            onUpdateProjectParameters(projParams)
                        },
                        enabled = enabled,
                        removeTestTag = "remove_project_param_$index",
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            RwButton(
                onClick = {
                    projParams = projParams + Parameter(key = "", value = "")
                    onUpdateProjectParameters(projParams)
                },
                variant = RwButtonVariant.Secondary,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("add_project_parameter_button"),
            ) {
                Text(packStringResource(Res.string.editor_project_add_parameter))
            }

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

        Spacer(Modifier.height(Spacing.sm))

        // Block ID
        var blockIdText by remember(block.id) { mutableStateOf(block.id.value) }
        if (blockIdText != block.id.value) blockIdText = block.id.value
        var blockIdError by remember(block.id) { mutableStateOf(false) }
        // Keep references fresh for modifier callbacks (onFocusChanged, onPreviewKeyEvent)
        val currentBlockId by rememberUpdatedState(block.id)
        val currentOnUpdateBlockId by rememberUpdatedState(onUpdateBlockId)

        fun commitBlockId() {
            if (blockIdText == currentBlockId.value) return
            val trimmed = blockIdText.trim('-')
            if (trimmed.isEmpty()) {
                blockIdText = currentBlockId.value
                blockIdError = false
            } else {
                val newId = BlockId(trimmed)
                val success = currentOnUpdateBlockId(currentBlockId, newId)
                if (!success) {
                    blockIdError = true
                } else {
                    blockIdText = trimmed
                    blockIdError = false
                }
            }
        }

        RwTextField(
            value = blockIdText,
            onValueChange = { raw ->
                val sanitized = raw.lowercase().replace(BlockIdSanitizeRegex, "-").trimStart('-')
                blockIdText = sanitized
                blockIdError = false
            },
            label = packStringResource(Res.string.editor_prop_block_id),
            placeholder = packStringResource(Res.string.editor_prop_block_id_placeholder),
            singleLine = true,
            enabled = enabled,
            isError = blockIdError,
            supportingText = if (blockIdError) {
                { Text(packStringResource(Res.string.editor_prop_block_id_duplicate)) }
            } else {
                { Text(
                    packStringResource(Res.string.editor_prop_block_id_hint),
                    style = AppTypography.bodySmall,
                ) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("block_id_field")
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        commitBlockId()
                        true
                    } else false
                }
                .onFocusChanged { state ->
                    if (!state.isFocused) commitBlockId()
                },
        )

        Spacer(Modifier.height(Spacing.lg))

        when (block) {
            is Block.ActionBlock -> {
                var selectedTab by remember(block.id) { mutableStateOf(0) }

                val configKey = block.type.configIdParameterKey()
                val isSlack = block.type == BlockType.SLACK_MESSAGE
                val visibleParamCount = remember(block.parameters, configKey, isSlack) {
                    block.parameters.count { p ->
                        (configKey == null || p.key != configKey) &&
                            (!isSlack || p.key != "text")
                    }
                }
                val gateCount = listOfNotNull(block.preGate, block.postGate).size

                val paramsLabel = if (visibleParamCount > 0) {
                    "${packStringResource(Res.string.editor_tab_parameters)} ($visibleParamCount)"
                } else {
                    packStringResource(Res.string.editor_tab_parameters)
                }
                val gatesLabel = if (gateCount > 0) {
                    "${packStringResource(Res.string.editor_tab_gates)} ($gateCount)"
                } else {
                    packStringResource(Res.string.editor_tab_gates)
                }
                val tabLabels = listOf(
                    packStringResource(Res.string.editor_tab_overview),
                    paramsLabel,
                    gatesLabel,
                )
                RwSegmentedTabRow(
                    tabs = tabLabels,
                    selectedIndex = selectedTab,
                    onTabSelected = { selectedTab = it },
                    enabled = enabled,
                    testTagPrefix = "properties_tab",
                )
                Spacer(Modifier.height(Spacing.sm))

                when (selectedTab) {
                    0 -> OverviewTabContent(
                        block = block,
                        graph = graph,
                        projectParameters = projectParameters,
                        connections = connections,
                        externalConfigs = externalConfigs,
                        isFetchingConfigs = isFetchingConfigs,
                        configFetchError = configFetchError,
                        onUpdateType = onUpdateType,
                        onUpdateConnectionId = onUpdateConnectionId,
                        onSelectConfig = onSelectConfig,
                        onRefreshConfigs = onRefreshConfigs,
                        onUpdateInjectWebhookUrl = onUpdateInjectWebhookUrl,
                        onUpdateParameters = onUpdateParameters,
                        onUpdateTimeout = onUpdateTimeout,
                        onUpdateDescription = onUpdateDescription,
                        enabled = enabled,
                    )
                    1 -> ParametersTabContent(
                        block = block,
                        graph = graph,
                        projectParameters = projectParameters,
                        isFetchingConfigParams = isFetchingConfigParams,
                        onRefreshConfigParams = onRefreshConfigParams,
                        onUpdateParameters = onUpdateParameters,
                        enabled = enabled,
                    )
                    2 -> GatesTabContent(
                        block = block,
                        graph = graph,
                        projectParameters = projectParameters,
                        onUpdatePreGate = onUpdatePreGate,
                        onUpdatePostGate = onUpdatePostGate,
                        enabled = enabled,
                    )
                }
            }
            is Block.ContainerBlock -> {
                var containerTab by remember(block.id) { mutableStateOf(0) }
                val gateCount = listOfNotNull(block.preGate, block.postGate).size
                val gatesLabel = if (gateCount > 0) {
                    "${packStringResource(Res.string.editor_tab_gates)} ($gateCount)"
                } else {
                    packStringResource(Res.string.editor_tab_gates)
                }
                RwSegmentedTabRow(
                    tabs = listOf(packStringResource(Res.string.editor_tab_overview), gatesLabel),
                    selectedIndex = containerTab,
                    onTabSelected = { containerTab = it },
                    enabled = enabled,
                    testTagPrefix = "container_tab",
                )
                Spacer(Modifier.height(Spacing.sm))

                when (containerTab) {
                    0 -> {
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
                        Spacer(Modifier.height(Spacing.lg))
                        BlockDescriptionSection(
                            block = block,
                            enabled = enabled,
                            onUpdateDescription = onUpdateDescription,
                        )
                    }
                    1 -> {
                        val predecessors = remember(graph, block.id) {
                            com.github.mr3zee.dag.findPredecessors(graph, block.id)
                        }
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
                        Spacer(Modifier.height(Spacing.lg))
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
        }
    }
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(scrollState),
    )
    // Centered hint when no block is selected
    if (block == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                packStringResource(Res.string.editor_prop_empty_hint),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
    }
}

@Composable
private fun BlockDescriptionSection(
    block: Block,
    enabled: Boolean,
    onUpdateDescription: (BlockId, String) -> Unit,
) {
    var descExpanded by remember(block.id) { mutableStateOf(block.description.isNotBlank()) }
    var description by remember(block.id) { mutableStateOf(block.description) }
    if (description != block.description) description = block.description

    if (!descExpanded) {
        RwButton(
            onClick = { descExpanded = true },
            variant = RwButtonVariant.Ghost,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("add_description_button"),
        ) {
            Text(
                if (block.description.isNotBlank()) block.description.lineSequence().first()
                else packStringResource(Res.string.editor_prop_add_description),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(packStringResource(Res.string.editor_prop_description), style = AppTypography.label)
            RwButton(
                onClick = { descExpanded = false },
                variant = RwButtonVariant.Ghost,
                contentPadding = PaddingValues(Spacing.xs),
                modifier = Modifier.testTag("collapse_description_button"),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        key(block.id) {
            RwMarkdownField(
                value = description,
                onValueChange = {
                    description = it
                    onUpdateDescription(block.id, it)
                },
                placeholder = packStringResource(Res.string.editor_prop_description_placeholder),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("block_description_field"),
                testTag = "block_description_field",
            )
        }
    }
}

@Composable
private fun OverviewTabContent(
    block: Block.ActionBlock,
    graph: DagGraph,
    projectParameters: List<Parameter>,
    connections: List<Connection>,
    externalConfigs: List<ExternalConfig>,
    isFetchingConfigs: Boolean,
    configFetchError: String?,
    onUpdateType: (BlockId, BlockType) -> Unit,
    onUpdateConnectionId: (BlockId, ConnectionId?) -> Unit,
    onSelectConfig: (BlockId, String) -> Unit,
    onRefreshConfigs: (BlockId) -> Unit,
    onUpdateInjectWebhookUrl: (BlockId, Boolean) -> Unit,
    onUpdateParameters: (BlockId, List<Parameter>) -> Unit,
    onUpdateTimeout: (BlockId, Long?) -> Unit,
    onUpdateDescription: (BlockId, String) -> Unit,
    enabled: Boolean,
) {
    // Compute predecessors for template picker (shared by gates, parameters, and Slack message)
    val predecessors = remember(graph, block.id) {
        com.github.mr3zee.dag.findPredecessors(graph, block.id)
    }

    // Type selector
    var typeExpanded by remember(block.id) { mutableStateOf(false) }
    val labelColor = LocalAppColors.current.chromeTextSecondary
    Text(packStringResource(Res.string.editor_prop_type), style = AppTypography.label, color = labelColor)
    Spacer(Modifier.height(Spacing.xs))
    Box {
        RwButton(
            onClick = { typeExpanded = true },
            variant = RwButtonVariant.Secondary,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("block_type_selector"),
        ) {
            Text(block.type.displayName())
        }
        RwDropdownMenu(
            expanded = typeExpanded,
            onDismissRequest = { typeExpanded = false },
        ) {
            BlockType.entries.forEach { type ->
                RwDropdownMenuItem(
                    text = { Text(type.displayName()) },
                    onClick = {
                        onUpdateType(block.id, type)
                        typeExpanded = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(Spacing.lg))

    // Connection selector
    val requiredConnectionType = block.type.requiredConnectionType()
    if (requiredConnectionType != null) {
        val filteredConnections = remember(connections, requiredConnectionType) {
            connections.filter { it.type == requiredConnectionType }
        }
        val selectedConnection = remember(filteredConnections, block.connectionId) {
            filteredConnections.find { it.id == block.connectionId }
        }
        var connExpanded by remember(block.id) { mutableStateOf(false) }

        Text(packStringResource(Res.string.editor_prop_connection), style = AppTypography.label, color = labelColor)
        Spacer(Modifier.height(Spacing.xs))
        Box {
            RwButton(
                onClick = { connExpanded = true },
                variant = RwButtonVariant.Secondary,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("block_connection_selector"),
            ) {
                Text(
                    selectedConnection?.name ?: packStringResource(Res.string.editor_prop_connection_select),
                    maxLines = 1,
                )
            }
            RwDropdownMenu(
                expanded = connExpanded,
                onDismissRequest = { connExpanded = false },
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    RwDropdownMenuItem(
                        text = { Text(packStringResource(Res.string.editor_prop_connection_none)) },
                        onClick = {
                            onUpdateConnectionId(block.id, null)
                            connExpanded = false
                        },
                    )
                    filteredConnections.forEach { conn ->
                        RwDropdownMenuItem(
                            text = { Text(conn.name) },
                            onClick = {
                                onUpdateConnectionId(block.id, conn.id)
                                connExpanded = false
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
    }

    // External config selector
    val configKey = block.type.configIdParameterKey()
    if (configKey != null && block.connectionId != null) {
        val selectedConfigId = block.parameters.find { it.key == configKey }?.value
        ExternalConfigSelector(
            configs = externalConfigs,
            selectedConfigId = selectedConfigId,
            isLoading = isFetchingConfigs,
            error = configFetchError,
            enabled = enabled,
            onSelect = { configId -> onSelectConfig(block.id, configId) },
            onRefresh = { onRefreshConfigs(block.id) },
        )

        Spacer(Modifier.height(Spacing.lg))
    }

    // Status webhook injection (TeamCity builds only)
    if (block.type == BlockType.TEAMCITY_BUILD) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RwCheckbox(
                checked = block.injectWebhookUrl,
                onCheckedChange = { checked ->
                    onUpdateInjectWebhookUrl(block.id, checked)
                },
                enabled = enabled,
                modifier = Modifier.testTag("inject_webhook_url_checkbox"),
            )
            Text(
                packStringResource(Res.string.editor_inject_webhook_label),
                style = AppTypography.label,
                color = labelColor,
            )
        }
        Text(
            packStringResource(Res.string.editor_inject_webhook_hint),
            style = AppTypography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
    }

    // Slack Message
    if (block.type == BlockType.SLACK_MESSAGE) {
        var slackMessage by remember(block.id) {
            mutableStateOf(block.parameters.find { it.key == "text" }?.value ?: "")
        }
        val currentTextValue = block.parameters.find { it.key == "text" }?.value ?: ""
        if (slackMessage != currentTextValue) slackMessage = currentTextValue

        TemplateAutocompleteField(
            value = slackMessage,
            onValueChange = { text ->
                slackMessage = text
                val currentIndex = block.parameters.indexOfFirst { it.key == "text" }
                val updatedParams = if (currentIndex >= 0) {
                    block.parameters.toMutableList().apply {
                        set(currentIndex, get(currentIndex).copy(value = text))
                    }
                } else {
                    block.parameters + Parameter(key = "text", value = text)
                }
                onUpdateParameters(block.id, updatedParams)
            },
            projectParameters = projectParameters,
            predecessors = predecessors,
            label = { Text(packStringResource(Res.string.editor_slack_message_label), style = AppTypography.label) },
            placeholder = packStringResource(Res.string.editor_slack_message_placeholder),
            singleLine = false,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            testTag = "slack_message_field",
        )
        Spacer(Modifier.height(Spacing.lg))
    }

    // Timeout
    val isTimeoutRequired = block.type.requiresTimeout()
    var timeoutText by remember(block.id) {
        mutableStateOf(block.timeoutSeconds?.toString() ?: "")
    }
    var timeoutTouched by remember(block.id) { mutableStateOf(false) }
    val timeoutLabel = if (isTimeoutRequired) {
        packStringResource(Res.string.editor_prop_timeout_required)
    } else {
        packStringResource(Res.string.editor_prop_timeout)
    }
    val isTimeoutError = isTimeoutRequired && timeoutTouched && timeoutText.isBlank()
    RwTextField(
        value = timeoutText,
        onValueChange = { text ->
            val filtered = text.filter { it.isDigit() }
            timeoutText = filtered
            timeoutTouched = true
            val seconds = filtered.toLongOrNull()
            onUpdateTimeout(block.id, seconds)
        },
        label = timeoutLabel,
        placeholder = timeoutLabel,
        singleLine = true,
        enabled = enabled,
        isError = isTimeoutError,
        supportingText = if (isTimeoutError) {
            { Text(packStringResource(Res.string.editor_prop_timeout_required_hint)) }
        } else null,
        modifier = Modifier.fillMaxWidth().testTag("block_timeout_field"),
    )

    // Description
    Spacer(Modifier.height(Spacing.lg))
    BlockDescriptionSection(
        block = block,
        enabled = enabled,
        onUpdateDescription = onUpdateDescription,
    )

    // Read-only outputs section — merge known system outputs with any custom outputs on the block
    val allOutputs = remember(block.type, block.outputs) {
        val known = block.type.knownOutputs()
        val knownNames = known.map { it.name }.toSet()
        val custom = block.outputs.filter { it.name !in knownNames }
        known + custom
    }
    if (allOutputs.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        HorizontalDivider(modifier = Modifier.padding(bottom = Spacing.sm))
        Text(packStringResource(Res.string.editor_outputs_header), style = AppTypography.subheading)
        Spacer(Modifier.height(Spacing.xs))
        allOutputs.forEach { output ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                Text(output.name, style = AppTypography.bodySmall)
                if (output.description.isNotEmpty()) {
                    Text(
                        output.description,
                        style = AppTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametersTabContent(
    block: Block.ActionBlock,
    graph: DagGraph,
    projectParameters: List<Parameter>,
    isFetchingConfigParams: Boolean,
    onRefreshConfigParams: (BlockId) -> Unit,
    onUpdateParameters: (BlockId, List<Parameter>) -> Unit,
    enabled: Boolean,
) {
    val configKey = block.type.configIdParameterKey()
    val predecessors = remember(graph, block.id) {
        com.github.mr3zee.dag.findPredecessors(graph, block.id)
    }

    // Parameters header with refresh button
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val paramLabelColor = LocalAppColors.current.chromeTextSecondary
        Text(
            packStringResource(Res.string.editor_prop_parameters),
            style = AppTypography.label,
            color = paramLabelColor,
            modifier = Modifier.weight(1f),
        )
        if (configKey != null && block.connectionId != null) {
            val currentConfigId = block.parameters.find { it.key == configKey }?.value
            if (isFetchingConfigParams) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                RwIconButton(
                    onClick = { onRefreshConfigParams(block.id) },
                    enabled = enabled && !currentConfigId.isNullOrBlank(),
                    modifier = Modifier.testTag("refresh_parameters_button"),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = packStringResource(Res.string.editor_refresh_parameters), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.xs))

    var params by remember(block.id) { mutableStateOf(block.parameters) }
    if (params != block.parameters) params = block.parameters

    // Filter out managed parameters from the visible list:
    // - config ID parameter (managed by the config selector)
    // - "text" parameter for Slack blocks (managed by the dedicated message field)
    val isSlack = block.type == BlockType.SLACK_MESSAGE
    val visibleParamsWithIndex = remember(params, configKey, isSlack) {
        params.withIndex()
            .filter { (_, p) ->
                (configKey == null || p.key != configKey) &&
                    (!isSlack || p.key != "text")
            }
            .toList()
    }

    visibleParamsWithIndex.forEach { (actualIndex, param) ->
        key(block.id, actualIndex) {
        ParameterRow(
            parameter = param,
            projectParameters = projectParameters,
            predecessors = predecessors,
            onUpdate = { updated ->
                params = params.toMutableList().apply { set(actualIndex, updated) }
                onUpdateParameters(block.id, params)
            },
            onRemove = {
                params = params.toMutableList().apply { removeAt(actualIndex) }
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
private fun GatesTabContent(
    block: Block.ActionBlock,
    graph: DagGraph,
    projectParameters: List<Parameter>,
    onUpdatePreGate: (BlockId, Gate?) -> Unit,
    onUpdatePostGate: (BlockId, Gate?) -> Unit,
    enabled: Boolean,
) {
    val predecessors = remember(graph, block.id) {
        com.github.mr3zee.dag.findPredecessors(graph, block.id)
    }

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

    Spacer(Modifier.height(Spacing.lg))

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

@Composable
private fun ExternalConfigSelector(
    configs: List<ExternalConfig>,
    selectedConfigId: String?,
    isLoading: Boolean,
    error: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val selectedConfig = remember(configs, selectedConfigId) {
        configs.find { it.id == selectedConfigId }
    }
    var searchText by remember(selectedConfigId) {
        mutableStateOf(selectedConfig?.path ?: selectedConfigId ?: "")
    }
    var dropdownExpanded by remember(selectedConfigId) { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is FocusInteraction.Focus && configs.isNotEmpty()) {
                dropdownExpanded = true
            }
        }
    }

    val configLabelColor = LocalAppColors.current.chromeTextSecondary
    Text(packStringResource(Res.string.editor_config_selector), style = AppTypography.label, color = configLabelColor)
    Spacer(Modifier.height(Spacing.xs))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            RwTextField(
                value = searchText,
                onValueChange = { text ->
                    searchText = text
                    dropdownExpanded = true
                },
                placeholder = packStringResource(Res.string.editor_config_selector_placeholder),
                singleLine = true,
                enabled = enabled && !isLoading,
                supportingText = when {
                    error != null -> {{ Text(error) }}
                    !isLoading && configs.isEmpty() && selectedConfigId == null -> {{ Text(packStringResource(Res.string.editor_config_no_configs)) }}
                    else -> null
                },
                isError = error != null,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().testTag("config_selector_field"),
                textStyle = AppTypography.bodySmall,
            )
            if (configs.isNotEmpty()) {
                val filtered = remember(configs, searchText) {
                    if (searchText.isBlank() || searchText == selectedConfig?.path) configs
                    else configs.filter {
                        it.name.contains(searchText, ignoreCase = true) ||
                            it.path.contains(searchText, ignoreCase = true)
                    }
                }
                RwDropdownMenu(
                    expanded = dropdownExpanded && filtered.isNotEmpty(),
                    onDismissRequest = { dropdownExpanded = false },
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        filtered.forEach { config ->
                            RwDropdownMenuItem(
                                text = {
                                    Column {
                                        Text(config.name, style = AppTypography.label, maxLines = 1)
                                        Text(
                                            config.path,
                                            style = AppTypography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                        )
                                    }
                                },
                                onClick = {
                                    searchText = config.path
                                    dropdownExpanded = false
                                    onSelect(config.id)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (isLoading) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            RwIconButton(
                onClick = onRefresh,
                enabled = enabled,
                modifier = Modifier.testTag("refresh_configs_button"),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = packStringResource(Res.string.editor_refresh_configurations), modifier = Modifier.size(16.dp))
            }
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
        Text(label, style = AppTypography.body)
    }

    if (isEnabled) {
        Spacer(Modifier.height(Spacing.sm))
        TemplateAutocompleteField(
            value = message,
            onValueChange = { text ->
                message = text
                onUpdate(gate.copy(message = text))
            },
            projectParameters = projectParameters,
            predecessors = predecessors,
            placeholder = packStringResource(Res.string.editor_gate_message),
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = AppTypography.bodySmall,
            testTag = "${testTagPrefix}_message_field",
        )
        Spacer(Modifier.height(Spacing.sm))

        val countValue = requiredCount.toIntOrNull()
        val isCountError = countValue == null || countValue < 1
        RwTextField(
            value = requiredCount,
            onValueChange = { text ->
                val filtered = text.filter { it.isDigit() }
                requiredCount = filtered
                val count = filtered.toIntOrNull()?.coerceAtLeast(1) ?: 1
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
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SimpleParameterCard(
    parameter: Parameter,
    keyPlaceholder: String,
    valuePlaceholder: String,
    onUpdate: (Parameter) -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean = true,
    removeTestTag: String = "remove_parameter_button",
    valueField: @Composable (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    var isHovered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.inputBorder, AppShapes.sm)
                .padding(Spacing.sm),
        ) {
            if (parameter.label.isNotEmpty()) {
                Text(parameter.label, style = AppTypography.label)
                Spacer(Modifier.height(Spacing.xs))
            }
            RwTextField(
                value = parameter.key,
                onValueChange = { onUpdate(parameter.copy(key = it)) },
                placeholder = parameter.label.ifEmpty { keyPlaceholder },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                textStyle = AppTypography.bodySmall,
            )
            Spacer(Modifier.height(Spacing.xs))
            if (valueField != null) {
                valueField()
            } else {
                RwTextField(
                    value = parameter.value,
                    onValueChange = { onUpdate(parameter.copy(value = it)) },
                    placeholder = valuePlaceholder,
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = AppTypography.bodySmall,
                )
            }
        }
        if (isHovered && enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Spacing.xs, y = -Spacing.xs)
                    .size(20.dp)
                    .background(colors.chromeSurface, CircleShape)
                    .border(1.dp, colors.inputBorder, CircleShape)
                    .clickable(onClick = onRemove)
                    .testTag(removeTestTag),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = packStringResource(Res.string.editor_prop_remove_description),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
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
    val supportingText: @Composable (() -> Unit)? = remember(parameter.description) {
        if (parameter.description.isNotEmpty()) {
            { Text(parameter.description, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null
    }

    SimpleParameterCard(
        parameter = parameter,
        keyPlaceholder = packStringResource(Res.string.editor_prop_key),
        valuePlaceholder = packStringResource(Res.string.editor_prop_value),
        onUpdate = onUpdate,
        onRemove = onRemove,
        enabled = enabled,
        valueField = {
            TemplateAutocompleteField(
                value = parameter.value,
                onValueChange = { onUpdate(parameter.copy(value = it)) },
                projectParameters = projectParameters,
                predecessors = predecessors,
                placeholder = packStringResource(Res.string.editor_prop_value),
                supportingText = supportingText,
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                textStyle = AppTypography.bodySmall,
                testTag = "param_value_field",
            )
        },
    )
}

private val BlockIdSanitizeRegex = Regex("[^a-z0-9-]")
