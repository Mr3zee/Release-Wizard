package com.github.mr3zee.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ProjectLockInfo
import com.github.mr3zee.api.UpdateProjectRequest
import com.github.mr3zee.api.parseLockConflict
import com.github.mr3zee.api.toUserMessage
import com.github.mr3zee.dag.DagValidator
import com.github.mr3zee.dag.ValidationError
import com.github.mr3zee.model.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface LockState {
    data object Acquiring : LockState
    data class Acquired(val info: ProjectLockInfo) : LockState
    data class LockedByOther(val info: ProjectLockInfo?) : LockState
    data object Released : LockState
    data object LockLost : LockState
}

class DagEditorViewModel(
    private val projectId: ProjectId,
    private val apiClient: ProjectApiClient,
    // todo claude: unused
    private val currentUserId: String? = null,
    private val canForceUnlock: Boolean = false,
) : ViewModel() {

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes
        private const val HEARTBEAT_MAX_RETRIES = 3
    }

    private val _project = MutableStateFlow<ProjectTemplate?>(null)
    val project: StateFlow<ProjectTemplate?> = _project

    private val _graph = MutableStateFlow(DagGraph())
    val graph: StateFlow<DagGraph> = _graph

    private val _selectedBlockIds = MutableStateFlow<Set<BlockId>>(emptySet())
    val selectedBlockIds: StateFlow<Set<BlockId>> = _selectedBlockIds

    private val _selectedEdgeIndex = MutableStateFlow<Int?>(null)
    val selectedEdgeIndex: StateFlow<Int?> = _selectedEdgeIndex

    private val _clipboard = MutableStateFlow<DagGraph?>(null)
    val clipboard: StateFlow<DagGraph?> = _clipboard

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

    // Lock state
    private val _lockState = MutableStateFlow<LockState>(LockState.Acquiring)
    val lockState: StateFlow<LockState> = _lockState

    val isReadOnly: StateFlow<Boolean> = _lockState.map { state ->
        state is LockState.LockedByOther || state is LockState.LockLost || state is LockState.Acquiring
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showForceUnlock: Boolean get() = canForceUnlock

    private var heartbeatJob: Job? = null

    fun loadProject() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                reloadProjectAndAcquireLock()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun reloadProjectAndAcquireLock() {
        val p = apiClient.getProject(projectId)
        _project.value = p
        _graph.value = p.dagGraph
        undoStack.clear()
        undoIndex = -1
        pushUndoState(p.dagGraph)
        _isDirty.value = false
        attemptAcquireLock()
    }

    private suspend fun attemptAcquireLock() {
        _lockState.value = LockState.Acquiring
        try {
            val lock = apiClient.acquireLock(projectId)
            _lockState.value = LockState.Acquired(lock)
            startHeartbeat()
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 409) {
                val conflict = e.parseLockConflict()
                _lockState.value = LockState.LockedByOther(conflict?.lock)
            } else {
                _lockState.value = LockState.LockedByOther(null)
            }
        } catch (_: Exception) {
            _lockState.value = LockState.LockedByOther(null)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS.milliseconds)
                var success = false
                for (retry in 0 until HEARTBEAT_MAX_RETRIES) {
                    try {
                        val updated = apiClient.heartbeatLock(projectId)
                        _lockState.value = LockState.Acquired(updated)
                        success = true
                        break
                    } catch (_: Exception) {
                        if (retry < HEARTBEAT_MAX_RETRIES - 1) {
                            delay(2000L.milliseconds) // Wait before retry
                        }
                    }
                }
                if (!success) {
                    _lockState.value = LockState.LockLost
                    return@launch
                }
            }
        }
    }

    fun releaseLock() {
        val wasAcquired = _lockState.value is LockState.Acquired
        heartbeatJob?.cancel()
        heartbeatJob = null
        _lockState.value = LockState.Released
        if (wasAcquired) {
            viewModelScope.launch {
                apiClient.releaseLock(projectId)
            }
        }
    }

    fun forceUnlock() {
        viewModelScope.launch {
            try {
                apiClient.forceReleaseLock(projectId)
                reloadProjectAndAcquireLock()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun retryAcquireLock() {
        viewModelScope.launch {
            try {
                reloadProjectAndAcquireLock()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun save() {
        if (isReadOnly.value) return
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
            } catch (e: ClientRequestException) {
                if (e.response.status.value == 409) {
                    val conflict = e.parseLockConflict()
                    if (conflict != null) {
                        _lockState.value = LockState.LockLost
                        heartbeatJob?.cancel()
                    }
                }
                _error.value = e.toUserMessage()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun reacquireAndSave() {
        viewModelScope.launch {
            _lockState.value = LockState.Acquiring
            try {
                val lock = apiClient.acquireLock(projectId)
                _lockState.value = LockState.Acquired(lock)
                startHeartbeat()
                // Now save with the re-acquired lock
                save()
            } catch (e: ClientRequestException) {
                if (e.response.status.value == 409) {
                    val conflict = e.parseLockConflict()
                    _lockState.value = LockState.LockedByOther(conflict?.lock)
                } else {
                    _lockState.value = LockState.LockLost
                }
                _error.value = "Could not re-acquire lock: ${e.toUserMessage()}"
            } catch (e: Exception) {
                _lockState.value = LockState.LockLost
                _error.value = "Could not re-acquire lock: ${e.toUserMessage()}"
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    // Block operations

    @OptIn(ExperimentalUuidApi::class)
    fun addBlock(type: BlockType, name: String, x: Float, y: Float) {
        if (isReadOnly.value) return
        val blockId = BlockId(Uuid.random().toString())
        val block = Block.ActionBlock(id = blockId, name = name, type = type)
        val position = BlockPosition(x, y)
        updateGraph(
            _graph.value.copy(
                blocks = _graph.value.blocks + block,
                positions = _graph.value.positions + (blockId to position),
            )
        )
        _selectedBlockIds.value = setOf(blockId)
        _selectedEdgeIndex.value = null
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addContainerBlock(name: String, x: Float, y: Float) {
        if (isReadOnly.value) return
        val blockId = BlockId(Uuid.random().toString())
        val block = Block.ContainerBlock(id = blockId, name = name)
        val position = BlockPosition(x, y)
        updateGraph(
            _graph.value.copy(
                blocks = _graph.value.blocks + block,
                positions = _graph.value.positions + (blockId to position),
            )
        )
        _selectedBlockIds.value = setOf(blockId)
        _selectedEdgeIndex.value = null
    }

    fun removeSelectedBlocks() {
        if (isReadOnly.value) return
        val blockIds = _selectedBlockIds.value
        if (blockIds.isEmpty()) return
        val g = _graph.value
        updateGraph(
            g.copy(
                blocks = g.blocks.filter { it.id !in blockIds },
                edges = g.edges.filter { it.fromBlockId !in blockIds && it.toBlockId !in blockIds },
                positions = g.positions.filterKeys { it !in blockIds },
            )
        )
        _selectedBlockIds.value = emptySet()
        _selectedEdgeIndex.value = null
    }

    fun removeSelectedEdge() {
        if (isReadOnly.value) return
        val idx = _selectedEdgeIndex.value ?: return
        val g = _graph.value
        if (idx in g.edges.indices) {
            updateGraph(g.copy(edges = g.edges.toMutableList().apply { removeAt(idx) }))
        }
        _selectedEdgeIndex.value = null
    }

    fun moveBlock(blockId: BlockId, dx: Float, dy: Float) {
        if (isReadOnly.value) return
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
        if (isReadOnly.value) return
        val g = _graph.value
        // Don't add duplicate or self-loop edges
        if (g.edges.any { it.fromBlockId == fromBlockId && it.toBlockId == toBlockId }) return
        if (fromBlockId == toBlockId) return
        updateGraph(g.copy(edges = g.edges + Edge(fromBlockId, toBlockId)))
    }

    fun selectBlock(blockId: BlockId?) {
        _selectedBlockIds.value = if (blockId != null) setOf(blockId) else emptySet()
        _selectedEdgeIndex.value = null
    }

    fun toggleBlockSelection(blockId: BlockId) {
        val current = _selectedBlockIds.value
        _selectedBlockIds.value = if (blockId in current) current - blockId else current + blockId
        _selectedEdgeIndex.value = null
    }

    fun selectEdge(index: Int?) {
        _selectedEdgeIndex.value = index
        _selectedBlockIds.value = emptySet()
    }

    // Property updates — mutate graph without flooding undo stack.
    // Undo tracks structural changes (add/remove blocks/edges, moves).

    fun updateBlockName(blockId: BlockId, name: String) {
        if (isReadOnly.value) return
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
        if (isReadOnly.value) return
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
        updateActionBlock(blockId) { it.copy(parameters = parameters) }
    }

    fun updateBlockTimeout(blockId: BlockId, timeoutSeconds: Long?) {
        updateActionBlock(blockId) { it.copy(timeoutSeconds = timeoutSeconds) }
    }

    fun updateBlockPreGate(blockId: BlockId, gate: Gate?) {
        updateActionBlock(blockId) { it.copy(preGate = gate) }
    }

    fun updateBlockPostGate(blockId: BlockId, gate: Gate?) {
        updateActionBlock(blockId) { it.copy(postGate = gate) }
    }

    private fun updateActionBlock(blockId: BlockId, transform: (Block.ActionBlock) -> Block.ActionBlock) {
        if (isReadOnly.value) return
        val g = _graph.value
        updateGraphSilent(
            g.copy(
                blocks = g.blocks.map { block ->
                    if (block.id == blockId && block is Block.ActionBlock) {
                        transform(block)
                    } else block
                }
            )
        )
    }

    fun undo() {
        if (isReadOnly.value) return
        if (undoIndex <= 0) return
        undoIndex--
        _graph.value = undoStack[undoIndex]
        _isDirty.value = true
        updateUndoRedoState()
        revalidate()
    }

    fun redo() {
        if (isReadOnly.value) return
        if (undoIndex >= undoStack.lastIndex) return
        undoIndex++
        _graph.value = undoStack[undoIndex]
        _isDirty.value = true
        updateUndoRedoState()
        revalidate()
    }

    fun copySelected() {
        val selected = _selectedBlockIds.value
        if (selected.isEmpty()) return
        val g = _graph.value
        val blocks = g.blocks.filter { it.id in selected }
        val edges = g.edges.filter { it.fromBlockId in selected && it.toBlockId in selected }
        val positions = g.positions.filterKeys { it in selected }
        _clipboard.value = DagGraph(blocks = blocks, edges = edges, positions = positions)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun pasteClipboard() {
        if (isReadOnly.value) return
        val clip = _clipboard.value ?: return
        val g = _graph.value

        val remapped = remapDagGraph(clip, emptyMap())
        // Offset positions to visually separate pasted blocks
        val offsetPositions = remapped.positions.mapValues { (_, pos) ->
            BlockPosition(pos.x + 30f, pos.y + 30f)
        }

        updateGraph(
            g.copy(
                blocks = g.blocks + remapped.blocks,
                edges = g.edges + remapped.edges,
                positions = g.positions + offsetPositions,
            )
        )

        _selectedBlockIds.value = remapped.blocks.map { it.id }.toSet()
        _selectedEdgeIndex.value = null
    }

    fun selectAll() {
        _selectedBlockIds.value = _graph.value.blocks.map { it.id }.toSet()
        _selectedEdgeIndex.value = null
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

    @OptIn(ExperimentalUuidApi::class)
    private fun remapDagGraph(graph: DagGraph, parentMapping: Map<BlockId, BlockId>): DagGraph {
        val childMapping = graph.blocks.associate { it.id to BlockId(Uuid.random().toString()) }
        val allMapping = parentMapping + childMapping

        val newBlocks = graph.blocks.map { block ->
            val newId = childMapping[block.id] ?: error("Missing ID mapping for block ${block.id}")
            when (block) {
                is Block.ActionBlock -> block.copy(id = newId)
                is Block.ContainerBlock -> block.copy(id = newId, children = remapDagGraph(block.children, allMapping))
            }
        }

        val newEdges = graph.edges.mapNotNull { edge ->
            val newFrom = childMapping[edge.fromBlockId] ?: return@mapNotNull null
            val newTo = childMapping[edge.toBlockId] ?: return@mapNotNull null
            Edge(fromBlockId = newFrom, toBlockId = newTo)
        }

        val newPositions = graph.positions.mapKeys { (oldId, _) ->
            childMapping[oldId] ?: error("Missing ID mapping for position $oldId")
        }

        return DagGraph(blocks = newBlocks, edges = newEdges, positions = newPositions)
    }
}
