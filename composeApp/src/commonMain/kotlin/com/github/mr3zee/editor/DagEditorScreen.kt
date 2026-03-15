package com.github.mr3zee.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.dag.ValidationError
import com.github.mr3zee.util.resolve
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DagEditorScreen(
    viewModel: DagEditorViewModel,
    onBack: () -> Unit,
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

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showForceUnlockDialog by remember { mutableStateOf(false) }
    var showLockLostDiscardDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = stringResource(Res.string.common_dismiss)
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

    val handleBack: () -> Unit = {
        when {
            lockState is LockState.LockLost && isDirty -> {
                showLockLostDiscardDialog = true
            }
            isDirty -> {
                showDiscardDialog = true
            }
            else -> {
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(project?.name ?: stringResource(Res.string.editor_loading))
                        if (isReadOnly) {
                            Text(
                                " " + stringResource(Res.string.editor_read_only),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else if (isDirty) {
                            Text(
                                " " + stringResource(Res.string.editor_dirty_indicator),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_navigate_back))
                        Text(stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    if (validationErrors.isNotEmpty()) {
                        ValidationErrorBadge(validationErrors)
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = isDirty && !isSaving && !isReadOnly,
                        modifier = Modifier.testTag("save_button"),
                    ) {
                        Text(if (isSaving) stringResource(Res.string.common_saving) else stringResource(Res.string.common_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier
            .testTag("dag_editor_screen")
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
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
                    Text(resolvedError ?: stringResource(Res.string.common_error), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadProject() }) { Text(stringResource(Res.string.common_retry)) }
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

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                // Left: toolbar / block palette
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

                VerticalDivider()

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

                VerticalDivider()

                // Right: properties panel
                BlockPropertiesPanel(
                    block = selectedBlock,
                    graph = graph,
                    projectParameters = project?.parameters ?: emptyList(),
                    onUpdateName = { id, name -> viewModel.updateBlockName(id, name) },
                    onUpdateType = { id, type -> viewModel.updateBlockType(id, type) },
                    onUpdateParameters = { id, params -> viewModel.updateBlockParameters(id, params) },
                    onUpdateTimeout = { id, timeout -> viewModel.updateBlockTimeout(id, timeout) },
                    onUpdatePreGate = { id, gate -> viewModel.updateBlockPreGate(id, gate) },
                    onUpdatePostGate = { id, gate -> viewModel.updateBlockPostGate(id, gate) },
                    enabled = !isReadOnly,
                )
            }
        }
    }

    // Unsaved changes confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(Res.string.common_unsaved_title)) },
            text = { Text(stringResource(Res.string.common_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(Res.string.common_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }

    // Lock-lost discard dialog
    if (showLockLostDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showLockLostDiscardDialog = false },
            title = { Text(stringResource(Res.string.editor_dialog_lock_expired_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.editor_dialog_lock_expired_body))
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            showLockLostDiscardDialog = false
                            onBack()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(Res.string.editor_dialog_lock_expired_discard))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLockLostDiscardDialog = false
                    viewModel.reacquireAndSave()
                }) {
                    Text(stringResource(Res.string.editor_dialog_reacquire_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockLostDiscardDialog = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }

    // Force unlock confirmation dialog
    if (showForceUnlockDialog) {
        val lockedByName = (lockState as? LockState.LockedByOther)?.info?.username ?: stringResource(Res.string.editor_lock_unknown_user)
        AlertDialog(
            onDismissRequest = { showForceUnlockDialog = false },
            title = { Text(stringResource(Res.string.editor_dialog_force_unlock_title)) },
            text = { Text(stringResource(Res.string.editor_dialog_force_unlock_body, lockedByName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForceUnlockDialog = false
                        viewModel.forceUnlock()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(Res.string.editor_dialog_force_unlock_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceUnlockDialog = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor,
                    )
                    Spacer(Modifier.width(8.dp))
                    // Self-lock must be checked first: when locked by self, username is also non-null
                    Text(
                        when {
                            isLockedBySelf -> stringResource(Res.string.editor_lock_self_session)
                            username != null -> stringResource(Res.string.editor_lock_by_other, username)
                            else -> stringResource(Res.string.editor_lock_acquire_failed)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                    ) {
                        Text(stringResource(Res.string.common_retry))
                    }
                    if (showForceUnlock) {
                        TextButton(
                            onClick = onForceUnlock,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isNetworkError) contentColor
                                    else MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.testTag("force_unlock_button"),
                        ) {
                            Text(stringResource(Res.string.editor_dialog_force_unlock_confirm))
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.editor_lock_lost_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReacquireAndSave) {
                        Text(stringResource(Res.string.editor_dialog_reacquire_save))
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
    var showDialog by remember { mutableStateOf(false) }

    TextButton(
        onClick = { showDialog = true },
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(pluralStringResource(Res.plurals.issues, errors.size, errors.size))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(Res.string.editor_validation_title)) },
            text = {
                Column {
                    errors.forEach { err ->
                        Text(
                            formatValidationError(err),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(Res.string.common_ok)) }
            },
        )
    }
}

@Composable
private fun formatValidationError(error: ValidationError): String = when (error) {
    is ValidationError.CycleDetected -> stringResource(Res.string.editor_validation_cycle, error.involvedBlockIds.size)
    is ValidationError.DuplicateBlockId -> stringResource(Res.string.editor_validation_duplicate_id, error.blockId.value)
    is ValidationError.InvalidEdgeReference -> stringResource(Res.string.editor_validation_invalid_edge, error.missingBlockId.value)
    is ValidationError.SelfLoop -> stringResource(Res.string.editor_validation_self_loop, error.edge.fromBlockId.value)
}
