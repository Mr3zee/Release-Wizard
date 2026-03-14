package com.github.mr3zee.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.dag.ValidationError

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

    LaunchedEffect(Unit) {
        viewModel.loadProject()
    }

    val selectedBlock = remember(selectedBlockIds, graph) {
        if (selectedBlockIds.size == 1) graph.blocks.find { it.id == selectedBlockIds.first() } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(project?.name ?: "Loading...")
                        if (isDirty) {
                            Text(
                                " *",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    if (validationErrors.isNotEmpty()) {
                        ValidationErrorBadge(validationErrors)
                    }
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = isDirty && !isSaving,
                        modifier = Modifier.testTag("save_button"),
                    ) {
                        Text(if (isSaving) "Saving..." else "Save")
                    }
                },
            )
        },
        modifier = Modifier
            .testTag("dag_editor_screen")
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    // Support both Ctrl (Windows/Linux) and Cmd (macOS)
                    val isModifier = event.isCtrlPressed || event.isMetaPressed
                    when {
                        event.key == Key.Delete || event.key == Key.Backspace -> {
                            if (selectedBlockIds.isNotEmpty()) viewModel.removeSelectedBlocks()
                            else if (selectedEdgeIndex != null) viewModel.removeSelectedEdge()
                            true
                        }
                        isModifier && event.key == Key.Z && !event.isShiftPressed -> {
                            viewModel.undo()
                            true
                        }
                        isModifier && event.key == Key.Z && event.isShiftPressed -> {
                            viewModel.redo()
                            true
                        }
                        isModifier && event.key == Key.S -> {
                            if (isDirty) viewModel.save()
                            true
                        }
                        isModifier && event.key == Key.C -> {
                            viewModel.copySelected()
                            true
                        }
                        isModifier && event.key == Key.V -> {
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
                    Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadProject() }) { Text("Retry") }
                }
            }
            return@Scaffold
        }

        // Error snackbar for save errors
        error?.let { msg ->
            if (project != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss")
                        }
                    },
                ) {
                    Text(msg)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
            )
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
        Text("${errors.size} issue${if (errors.size > 1) "s" else ""}")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Validation Issues") },
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
                TextButton(onClick = { showDialog = false }) { Text("OK") }
            },
        )
    }
}

private fun formatValidationError(error: ValidationError): String = when (error) {
    is ValidationError.CycleDetected -> "Cycle detected involving ${error.involvedBlockIds.size} blocks"
    is ValidationError.DuplicateBlockId -> "Duplicate block ID: ${error.blockId.value}"
    is ValidationError.InvalidEdgeReference -> "Edge references missing block: ${error.missingBlockId.value}"
    is ValidationError.SelfLoop -> "Self-loop on block: ${error.edge.fromBlockId.value}"
}
