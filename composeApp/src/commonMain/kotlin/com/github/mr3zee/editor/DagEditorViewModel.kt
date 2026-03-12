package com.github.mr3zee.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.UpdateProjectRequest
import com.github.mr3zee.dag.DagValidator
import com.github.mr3zee.dag.ValidationError
import com.github.mr3zee.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class DagEditorViewModel(
    private val projectId: ProjectId,
    private val apiClient: ProjectApiClient,
) : ViewModel() {

    private val _project = MutableStateFlow<ProjectTemplate?>(null)
    val project: StateFlow<ProjectTemplate?> = _project

    private val _graph = MutableStateFlow(DagGraph())
    val graph: StateFlow<DagGraph> = _graph

    private val _selectedBlockId = MutableStateFlow<BlockId?>(null)
    val selectedBlockId: StateFlow<BlockId?> = _selectedBlockId

    private val _selectedEdgeIndex = MutableStateFlow<Int?>(null)
    val selectedEdgeIndex: StateFlow<Int?> = _selectedEdgeIndex

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _validationErrors = MutableStateFlow<List<ValidationError>>(emptyList())
    val validationErrors: StateFlow<List<ValidationError>> = _validationErrors

    // Undo/redo
    private val undoStack = mutableListOf<DagGraph>()
    private var undoIndex = -1

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    fun loadProject() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val p = apiClient.getProject(projectId)
                _project.value = p
                _graph.value = p.dagGraph
                // Clear undo stack on load to prevent undo to stale state
                undoStack.clear()
                undoIndex = -1
                pushUndoState(p.dagGraph)
                _isDirty.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load project"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun save() {
        val p = _project.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                val updated = apiClient.updateProject(
                    p.id,
                    UpdateProjectRequest(dagGraph = _graph.value),
                )
                _project.value = updated
                _isDirty.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    // Block operations

    @OptIn(ExperimentalUuidApi::class)
    fun addBlock(type: BlockType, name: String, x: Float, y: Float) {
        val blockId = BlockId(Uuid.random().toString())
        val block = Block.ActionBlock(id = blockId, name = name, type = type)
        val position = BlockPosition(x, y)
        updateGraph(
            _graph.value.copy(
                blocks = _graph.value.blocks + block,
                positions = _graph.value.positions + (blockId to position),
            )
        )
        _selectedBlockId.value = blockId
        _selectedEdgeIndex.value = null
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addContainerBlock(name: String, x: Float, y: Float) {
        val blockId = BlockId(Uuid.random().toString())
        val block = Block.ContainerBlock(id = blockId, name = name)
        val position = BlockPosition(x, y)
        updateGraph(
            _graph.value.copy(
                blocks = _graph.value.blocks + block,
                positions = _graph.value.positions + (blockId to position),
            )
        )
        _selectedBlockId.value = blockId
        _selectedEdgeIndex.value = null
    }

    fun removeSelectedBlock() {
        val blockId = _selectedBlockId.value ?: return
        val g = _graph.value
        updateGraph(
            g.copy(
                blocks = g.blocks.filter { it.id != blockId },
                edges = g.edges.filter { it.fromBlockId != blockId && it.toBlockId != blockId },
                positions = g.positions - blockId,
            )
        )
        _selectedBlockId.value = null
        _selectedEdgeIndex.value = null
    }

    fun removeSelectedEdge() {
        val idx = _selectedEdgeIndex.value ?: return
        val g = _graph.value
        if (idx in g.edges.indices) {
            updateGraph(g.copy(edges = g.edges.toMutableList().apply { removeAt(idx) }))
        }
        _selectedEdgeIndex.value = null
    }

    fun moveBlock(blockId: BlockId, dx: Float, dy: Float) {
        val g = _graph.value
        val current = g.positions[blockId] ?: return
        val newPos = BlockPosition(current.x + dx, current.y + dy)
        // Move without pushing to undo stack (would flood during drag)
        _graph.value = g.copy(positions = g.positions + (blockId to newPos))
        _isDirty.value = true
    }

    fun commitMove() {
        pushUndoState(_graph.value)
        revalidate()
    }

    fun addEdge(fromBlockId: BlockId, toBlockId: BlockId) {
        val g = _graph.value
        // Don't add duplicate or self-loop edges
        if (g.edges.any { it.fromBlockId == fromBlockId && it.toBlockId == toBlockId }) return
        if (fromBlockId == toBlockId) return
        updateGraph(g.copy(edges = g.edges + Edge(fromBlockId, toBlockId)))
    }

    fun selectBlock(blockId: BlockId?) {
        _selectedBlockId.value = blockId
        _selectedEdgeIndex.value = null
    }

    fun selectEdge(index: Int?) {
        _selectedEdgeIndex.value = index
        _selectedBlockId.value = null
    }

    // Property updates — mutate graph without flooding undo stack.
    // Undo tracks structural changes (add/remove blocks/edges, moves).

    fun updateBlockName(blockId: BlockId, name: String) {
        val g = _graph.value
        updateGraphSilent(
            g.copy(
                blocks = g.blocks.map { block ->
                    if (block.id != blockId) return@map block
                    when (block) {
                        is Block.ActionBlock -> block.copy(name = name)
                        is Block.ContainerBlock -> block.copy(name = name)
                    }
                }
            )
        )
    }

    fun updateBlockType(blockId: BlockId, type: BlockType) {
        val g = _graph.value
        updateGraph(
            g.copy(
                blocks = g.blocks.map { block ->
                    if (block.id == blockId && block is Block.ActionBlock) {
                        block.copy(type = type)
                    } else block
                }
            )
        )
    }

    fun updateBlockParameters(blockId: BlockId, parameters: List<Parameter>) {
        val g = _graph.value
        updateGraphSilent(
            g.copy(
                blocks = g.blocks.map { block ->
                    if (block.id == blockId && block is Block.ActionBlock) {
                        block.copy(parameters = parameters)
                    } else block
                }
            )
        )
    }

    fun updateBlockTimeout(blockId: BlockId, timeoutSeconds: Long?) {
        val g = _graph.value
        updateGraphSilent(
            g.copy(
                blocks = g.blocks.map { block ->
                    if (block.id == blockId && block is Block.ActionBlock) {
                        block.copy(timeoutSeconds = timeoutSeconds)
                    } else block
                }
            )
        )
    }

    fun undo() {
        if (undoIndex <= 0) return
        undoIndex--
        _graph.value = undoStack[undoIndex]
        _isDirty.value = true
        updateUndoRedoState()
        revalidate()
    }

    fun redo() {
        if (undoIndex >= undoStack.lastIndex) return
        undoIndex++
        _graph.value = undoStack[undoIndex]
        _isDirty.value = true
        updateUndoRedoState()
        revalidate()
    }

    /** Compute next block placement position avoiding overlap with existing blocks. */
    fun nextPlacementPosition(): Pair<Float, Float> {
        val positions = _graph.value.positions.values
        if (positions.isEmpty()) return 100f to 100f
        val maxX = positions.maxOf { it.x }
        val maxY = positions.maxOf { it.y }
        // Place to the right of the rightmost block, or below if getting too wide
        return if (maxX < 800f) {
            (maxX + 220f) to 100f
        } else {
            100f to (maxY + 120f)
        }
    }

    /** Structural change — pushes to undo stack. */
    private fun updateGraph(newGraph: DagGraph) {
        _graph.value = newGraph
        _isDirty.value = true
        pushUndoState(newGraph)
        revalidate()
    }

    /** Property change — marks dirty but does NOT push undo (avoids flood on every keystroke). */
    private fun updateGraphSilent(newGraph: DagGraph) {
        _graph.value = newGraph
        _isDirty.value = true
        revalidate()
    }

    private fun pushUndoState(graph: DagGraph) {
        while (undoStack.size > undoIndex + 1) {
            undoStack.removeLast()
        }
        undoStack.add(graph)
        if (undoStack.size > 50) {
            undoStack.removeFirst()
        }
        undoIndex = undoStack.lastIndex
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoIndex > 0
        _canRedo.value = undoIndex < undoStack.lastIndex
    }

    private fun revalidate() {
        _validationErrors.value = DagValidator.validate(_graph.value)
    }
}
