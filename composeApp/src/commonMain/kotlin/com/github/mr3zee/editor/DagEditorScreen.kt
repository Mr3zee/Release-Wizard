package com.github.mr3zee.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.dag.ValidationError
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.HostOS
import com.github.mr3zee.util.currentHostOS
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DagEditorScreen(
    viewModel: DagEditorViewModel,
    onBack: () -> Unit,
    onOpenAutomation: (() -> Unit)? = null,
) {
    val project by viewModel.project.collectAsState()
    val graph by viewModel.graph.collectAsState()
    val selectedBlockIds by viewModel.selectedBlockIds.collectAsState()
    val selectedEdgeIndex by viewModel.selectedEdgeIndex.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val lockState by viewModel.lockState.collectAsState()
    val isReadOnly by viewModel.isReadOnly.collectAsState()
    val isLockedBySelf by viewModel.isLockedBySelf.collectAsState()
    val teamConnections by viewModel.teamConnections.collectAsState()
    val externalConfigs by viewModel.externalConfigs.collectAsState()
    val isFetchingConfigs by viewModel.isFetchingConfigs.collectAsState()
    val configFetchError by viewModel.configFetchError.collectAsState()
    val isFetchingConfigParams by viewModel.isFetchingConfigParams.collectAsState()

    val appColors = LocalAppColors.current

    var leftSidebarExpanded by remember { mutableStateOf(true) }
    var rightSidebarExpanded by remember { mutableStateOf(true) }

    var pendingDiscardNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showForceUnlockDialog by remember { mutableStateOf(false) }
    var pendingLockLostNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    val isConfirmationVisible = pendingDiscardNavigation != null || pendingLockLostNavigation != null || showForceUnlockDialog

    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = packStringResource(Res.string.common_dismiss)
    val resolvedError = error?.resolve()

    // Show transient errors via snackbar
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        if (project != null) {
            snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = dismissLabel,
                duration = SnackbarDuration.Long,
            )
            viewModel.dismissError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProject()
    }

    val selectedBlock = remember(selectedBlockIds, graph) {
        if (selectedBlockIds.size == 1) graph.blocks.find { it.id == selectedBlockIds.first() } else null
    }

    val currentIsDirty by rememberUpdatedState(isDirty)
    val currentLockState by rememberUpdatedState(lockState)
    val currentIsSaving by rememberUpdatedState(isSaving)
    val guardedNavigate: (() -> Unit) -> Unit = remember {
        { destination ->
            when {
                currentIsSaving -> { /* block navigation while save is in-flight */ }
                currentLockState is LockState.LockLost && currentIsDirty -> {
                    pendingLockLostNavigation = destination
                }
                currentIsDirty -> {
                    pendingDiscardNavigation = destination
                }
                else -> {
                    destination()
                }
            }
        }
    }
    val handleBack: () -> Unit = remember(onBack) { { guardedNavigate(onBack) } }

    val shortcutActions = remember(isConfirmationVisible, isDirty, isSaving, isReadOnly) {
        ShortcutActions(
            onSave = { if (isDirty && !isSaving && !isReadOnly) viewModel.save() },
            hasDialogOpen = isConfirmationVisible,
        )
    }
    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(project?.name ?: packStringResource(Res.string.editor_loading))
                        if (isReadOnly) {
                            Text(
                                " " + packStringResource(Res.string.editor_read_only),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = AppTypography.body,
                            )
                        } else if (isDirty) {
                            Text(
                                " " + packStringResource(Res.string.editor_dirty_indicator),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    RwButton(onClick = handleBack, variant = RwButtonVariant.Ghost) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                        Text(packStringResource(Res.string.common_back))
                    }
                },
                actions = {
                    if (validationErrors.isNotEmpty()) {
                        ValidationErrorBadge(validationErrors)
                    }
                    if (onOpenAutomation != null) {
                        RwButton(
                            onClick = { guardedNavigate(onOpenAutomation) },
                            variant = RwButtonVariant.Ghost,
                            modifier = Modifier.testTag("automation_button"),
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = packStringResource(Res.string.automation_open_button))
                            Text(packStringResource(Res.string.automation_open_button))
                        }
                    }
                    val saveMod = if (currentHostOS() == HostOS.MACOS) "\u2318" else "Ctrl"
                    RwTooltip(tooltip = "$saveMod+S") {
                        RwButton(
                            onClick = { viewModel.save() },
                            variant = if (isDirty) RwButtonVariant.Primary else RwButtonVariant.Ghost,
                            enabled = isDirty && !isSaving && !isReadOnly,
                            modifier = Modifier.testTag("save_button"),
                        ) {
                            Text(if (isSaving) packStringResource(Res.string.common_saving) else packStringResource(Res.string.common_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier
            .testTag("dag_editor_screen")
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    // Suppress destructive keys while a confirmation banner is showing
                    if (isConfirmationVisible && (event.key == Key.Delete || event.key == Key.Backspace)) {
                        return@onKeyEvent false
                    }
                    // Support both Ctrl (Windows/Linux) and Cmd (macOS)
                    val isModifier = event.isCtrlPressed || event.isMetaPressed
                    when {
                        !isReadOnly && (event.key == Key.Delete || event.key == Key.Backspace) -> {
                            if (selectedBlockIds.isNotEmpty()) viewModel.removeSelectedBlocks()
                            else if (selectedEdgeIndex != null) viewModel.removeSelectedEdge()
                            true
                        }
                        !isReadOnly && isModifier && event.key == Key.Z && !event.isShiftPressed -> {
                            viewModel.undo()
                            true
                        }
                        !isReadOnly && isModifier && event.key == Key.Z && event.isShiftPressed -> {
                            viewModel.redo()
                            true
                        }
                        !isReadOnly && isModifier && event.key == Key.S -> {
                            if (isDirty) viewModel.save()
                            true
                        }
                        isModifier && event.key == Key.C -> {
                            viewModel.copySelected()
                            true
                        }
                        !isReadOnly && isModifier && event.key == Key.V -> {
                            viewModel.pasteClipboard()
                            true
                        }
                        isModifier && event.key == Key.A -> {
                            viewModel.selectAll()
                            true
                        }
                        else -> false
                    }
                } else false
            },
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (error != null && project == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(resolvedError ?: packStringResource(Res.string.common_error), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(Spacing.sm))
                    RwButton(onClick = { viewModel.loadProject() }, variant = RwButtonVariant.Primary) { Text(packStringResource(Res.string.common_retry)) }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Lock banners
            EditLockBanner(
                lockState = lockState,
                isLockedBySelf = isLockedBySelf,
                showForceUnlock = viewModel.showForceUnlock,
                onForceUnlock = { showForceUnlockDialog = true },
                onRetry = { viewModel.retryAcquireLock() },
                onReacquireAndSave = { viewModel.reacquireAndSave() },
            )

            // Inline confirmation banners
            RwInlineConfirmation(
                visible = pendingDiscardNavigation != null,
                message = packStringResource(Res.string.common_unsaved_message),
                confirmLabel = packStringResource(Res.string.common_discard),
                onConfirm = {
                    val nav = pendingDiscardNavigation
                    pendingDiscardNavigation = null
                    nav?.invoke()
                },
                onDismiss = { pendingDiscardNavigation = null },
                isDestructive = true,
                testTag = "discard_confirm",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

            RwInlineConfirmation(
                visible = pendingLockLostNavigation != null,
                message = packStringResource(Res.string.editor_dialog_lock_expired_body),
                confirmLabel = packStringResource(Res.string.editor_dialog_lock_expired_discard),
                onConfirm = {
                    val nav = pendingLockLostNavigation
                    pendingLockLostNavigation = null
                    nav?.invoke()
                },
                onDismiss = { pendingLockLostNavigation = null },
                isDestructive = true,
                extraAction = Pair(packStringResource(Res.string.editor_dialog_reacquire_save)) {
                    pendingLockLostNavigation = null
                    viewModel.reacquireAndSave()
                },
                testTag = "lock_lost_confirm",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

            val forceUnlockLockedByName = (lockState as? LockState.LockedByOther)?.info?.username
                ?: packStringResource(Res.string.editor_lock_unknown_user)
            RwInlineConfirmation(
                visible = showForceUnlockDialog,
                message = packStringResource(Res.string.editor_dialog_force_unlock_body, forceUnlockLockedByName),
                confirmLabel = packStringResource(Res.string.editor_dialog_force_unlock_confirm),
                onConfirm = {
                    showForceUnlockDialog = false
                    viewModel.forceUnlock()
                },
                onDismiss = { showForceUnlockDialog = false },
                isDestructive = true,
                testTag = "force_unlock_confirm",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                // Left: toolbar / block palette
                AnimatedVisibility(
                    visible = leftSidebarExpanded,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally(),
                ) {
                    EditorToolbar(
                        onAddBlock = { type, name ->
                            val (x, y) = viewModel.nextPlacementPosition()
                            viewModel.addBlock(type, name, x, y)
                        },
                        onAddContainer = { name ->
                            val (x, y) = viewModel.nextPlacementPosition()
                            viewModel.addContainerBlock(name, x, y)
                        },
                        onDelete = {
                            if (selectedBlockIds.isNotEmpty()) viewModel.removeSelectedBlocks()
                            else if (selectedEdgeIndex != null) viewModel.removeSelectedEdge()
                        },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onCopy = { viewModel.copySelected() },
                        onPaste = { viewModel.pasteClipboard() },
                        canUndo = canUndo,
                        canRedo = canRedo,
                        hasSelection = selectedBlockIds.isNotEmpty() || selectedEdgeIndex != null,
                        hasClipboard = clipboard != null,
                        enabled = !isReadOnly,
                    )
                }

                // Left sidebar toggle + border
                Column(
                    Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .width(1.dp)
                            .drawBehind { drawRect(appColors.chromeBorder) }
                    )
                    RwTooltip(
                        tooltip = packStringResource(
                            if (leftSidebarExpanded) Res.string.editor_collapse_toolbar
                            else Res.string.editor_expand_toolbar
                        ),
                    ) {
                        RwIconButton(
                            onClick = { leftSidebarExpanded = !leftSidebarExpanded },
                            modifier = Modifier.size(44.dp).testTag("toggle_left_sidebar"),
                        ) {
                            Icon(
                                if (leftSidebarExpanded) Icons.AutoMirrored.Filled.KeyboardArrowLeft
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = packStringResource(
                                    if (leftSidebarExpanded) Res.string.editor_collapse_toolbar
                                    else Res.string.editor_expand_toolbar
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .width(1.dp)
                            .drawBehind { drawRect(appColors.chromeBorder) }
                    )
                }

                // Center: canvas
                DagCanvas(
                    graph = graph,
                    selectedBlockIds = selectedBlockIds,
                    selectedEdgeIndex = selectedEdgeIndex,
                    onSelectBlock = { viewModel.selectBlock(it) },
                    onToggleBlockSelection = { viewModel.toggleBlockSelection(it) },
                    onSelectEdge = { viewModel.selectEdge(it) },
                    onMoveBlock = { id, dx, dy -> viewModel.moveBlock(id, dx, dy) },
                    onCommitMove = { viewModel.commitMove() },
                    onAddEdge = { from, to -> viewModel.addEdge(from, to) },
                    isReadOnly = isReadOnly,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("dag_canvas"),
                )

                // Right sidebar toggle + border
                Column(
                    Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .width(1.dp)
                            .drawBehind { drawRect(appColors.chromeBorder) }
                    )
                    RwTooltip(
                        tooltip = packStringResource(
                            if (rightSidebarExpanded) Res.string.editor_collapse_properties
                            else Res.string.editor_expand_properties
                        ),
                    ) {
                        RwIconButton(
                            onClick = { rightSidebarExpanded = !rightSidebarExpanded },
                            modifier = Modifier.size(44.dp).testTag("toggle_right_sidebar"),
                        ) {
                            Icon(
                                if (rightSidebarExpanded) Icons.AutoMirrored.Filled.KeyboardArrowRight
                                else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = packStringResource(
                                    if (rightSidebarExpanded) Res.string.editor_collapse_properties
                                    else Res.string.editor_expand_properties
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .width(1.dp)
                            .drawBehind { drawRect(appColors.chromeBorder) }
                    )
                }

                // Right: properties panel
                AnimatedVisibility(
                    visible = rightSidebarExpanded,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally(),
                ) {
                    BlockPropertiesPanel(
                        block = selectedBlock,
                        graph = graph,
                        projectParameters = project?.parameters ?: emptyList(),
                        connections = teamConnections,
                        externalConfigs = if (selectedBlock != null) externalConfigs[selectedBlock.id] ?: emptyList() else emptyList(),
                        isFetchingConfigs = selectedBlock != null && selectedBlock.id in isFetchingConfigs,
                        configFetchError = if (selectedBlock != null) configFetchError[selectedBlock.id] else null,
                        isFetchingConfigParams = selectedBlock != null && selectedBlock.id in isFetchingConfigParams,
                        onUpdateName = { id, name -> viewModel.updateBlockName(id, name) },
                        onUpdateType = { id, type -> viewModel.updateBlockType(id, type) },
                        onUpdateConnectionId = { id, connId -> viewModel.updateBlockConnectionId(id, connId) },
                        onSelectConfig = { id, configId -> viewModel.selectExternalConfig(id, configId) },
                        onRefreshConfigs = { id -> viewModel.fetchExternalConfigs(id) },
                        onRefreshConfigParams = { id -> viewModel.fetchExternalConfigParameters(id) },
                        onUpdateParameters = { id, params -> viewModel.updateBlockParameters(id, params) },
                        onUpdateTimeout = { id, timeout -> viewModel.updateBlockTimeout(id, timeout) },
                        onUpdatePreGate = { id, gate -> viewModel.updateBlockPreGate(id, gate) },
                        onUpdatePostGate = { id, gate -> viewModel.updateBlockPostGate(id, gate) },
                        onUpdateInjectWebhookUrl = { id, inject -> viewModel.updateBlockInjectWebhookUrl(id, inject) },
                        enabled = !isReadOnly,
                    )
                }
            }
        }
    }

    } // ProvideShortcutActions
}

@Composable
private fun EditLockBanner(
    lockState: LockState,
    isLockedBySelf: Boolean,
    showForceUnlock: Boolean,
    onForceUnlock: () -> Unit,
    onRetry: () -> Unit,
    onReacquireAndSave: () -> Unit,
) {
    when (lockState) {
        is LockState.LockedByOther -> {
            val username = lockState.info?.username
            val isNetworkError = lockState.info == null
            val containerColor = if (isNetworkError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.tertiaryContainer
            val contentColor = if (isNetworkError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onTertiaryContainer
            Surface(
                color = containerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_lock_banner"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = packStringResource(Res.string.editor_lock_icon),
                        modifier = Modifier.size(16.dp),
                        tint = contentColor,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    // Self-lock must be checked first: when locked by self, username is also non-null
                    Text(
                        when {
                            isLockedBySelf -> packStringResource(Res.string.editor_lock_self_session)
                            username != null -> packStringResource(Res.string.editor_lock_by_other, username)
                            else -> packStringResource(Res.string.editor_lock_acquire_failed)
                        },
                        style = AppTypography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                    RwButton(
                        onClick = onRetry,
                        variant = RwButtonVariant.Ghost,
                        contentColor = contentColor,
                    ) {
                        Text(packStringResource(Res.string.common_retry))
                    }
                    if (showForceUnlock) {
                        RwButton(
                            onClick = onForceUnlock,
                            variant = RwButtonVariant.Ghost,
                            contentColor = if (isNetworkError) contentColor
                                else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("force_unlock_button"),
                        ) {
                            Text(packStringResource(Res.string.editor_dialog_force_unlock_confirm))
                        }
                    }
                }
            }
        }
        is LockState.LockLost -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lock_lost_banner"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = packStringResource(Res.string.editor_lock_icon),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        packStringResource(Res.string.editor_lock_lost_banner),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    RwButton(
                        onClick = onReacquireAndSave,
                        variant = RwButtonVariant.Ghost,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(packStringResource(Res.string.editor_dialog_reacquire_save))
                    }
                }
            }
        }
        else -> {
            // No banner for Acquiring, Acquired, Released
        }
    }
}

@Composable
private fun ValidationErrorBadge(errors: List<ValidationError>) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        RwButton(
            onClick = { showMenu = true },
            variant = RwButtonVariant.Ghost,
            contentColor = MaterialTheme.colorScheme.error,
        ) {
            Text(packPluralStringResource(Res.plurals.issues, errors.size, errors.size))
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 250.dp)
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(
                    packStringResource(Res.string.editor_validation_title),
                    style = AppTypography.subheading,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                errors.forEach { err ->
                    Text(
                        formatValidationError(err),
                        style = AppTypography.bodySmall,
                        modifier = Modifier.padding(vertical = Spacing.xxs),
                    )
                }
            }
        }
    }
}

@Composable
private fun formatValidationError(error: ValidationError): String = when (error) {
    is ValidationError.CycleDetected -> packStringResource(Res.string.editor_validation_cycle, error.involvedBlockIds.size)
    is ValidationError.DuplicateBlockId -> packStringResource(Res.string.editor_validation_duplicate_id, error.blockId.value)
    is ValidationError.InvalidEdgeReference -> packStringResource(Res.string.editor_validation_invalid_edge, error.missingBlockId.value)
    is ValidationError.SelfLoop -> packStringResource(Res.string.editor_validation_self_loop, error.edge.fromBlockId.value)
    is ValidationError.TooManyBlocks -> packStringResource(Res.string.editor_validation_too_many_blocks, error.count, error.max)
    is ValidationError.TooManyEdges -> packStringResource(Res.string.editor_validation_too_many_edges, error.count, error.max)
    is ValidationError.NestingTooDeep -> packStringResource(Res.string.editor_validation_nesting_too_deep, error.depth, error.max)
    is ValidationError.BlockNameTooLong -> packStringResource(Res.string.editor_validation_block_name_too_long, error.length, error.max)
    is ValidationError.TooManyParameters -> packStringResource(Res.string.editor_validation_too_many_parameters, error.max)
    is ValidationError.ParameterKeyTooLong -> packStringResource(Res.string.editor_validation_parameter_key_too_long, error.max)
    is ValidationError.ParameterValueTooLong -> packStringResource(Res.string.editor_validation_parameter_value_too_long, error.max)
}
