package com.github.mr3zee.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.ExternalConfig
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ProjectLockInfo
import com.github.mr3zee.api.UpdateProjectRequest
import com.github.mr3zee.api.parseLockConflict
import com.github.mr3zee.api.toParameter
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.util.UiMessage
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
    private val connectionApiClient: ConnectionApiClient? = null,
    private val currentUserId: String? = null,
    private val canForceUnlock: Boolean = false,
    autoSaveDebounceMs: Long = AUTO_SAVE_DEBOUNCE_MS,
) : ViewModel() {

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes
        private const val HEARTBEAT_MAX_RETRIES = 3
        const val AUTO_SAVE_DEBOUNCE_MS = 3_000L
    }

    private val _project = MutableStateFlow<ProjectTemplate?>(null)
    val project: StateFlow<ProjectTemplate?> = _project

    private val _graph = MutableStateFlow(DagGraph())
    val graph: StateFlow<DagGraph> = _graph

    private val _selectedBlockIds = MutableStateFlow<Set<BlockId>>(emptySet())
    val selectedBlockIds: StateFlow<Set<BlockId>> = _selectedBlockIds

    private val _selectedEdgeIndex = MutableStateFlow<Int?>(null)
    val selectedEdgeIndex: StateFlow<Int?> = _selectedEdgeIndex

    // Which container the selected edge belongs to (null = top-level)
    private val _selectedEdgeContainerId = MutableStateFlow<BlockId?>(null)
    val selectedEdgeContainerId: StateFlow<BlockId?> = _selectedEdgeContainerId

    private val _clipboard = MutableStateFlow<DagGraph?>(null)
    val clipboard: StateFlow<DagGraph?> = _clipboard

    // Parent lookup: blockId -> containerId (null if top-level)
    private val _parentLookup = MutableStateFlow<Map<BlockId, BlockId>>(emptyMap())
    val parentLookup: StateFlow<Map<BlockId, BlockId>> = _parentLookup

    // Container drag feedback state
    private val _hoveredContainerId = MutableStateFlow<BlockId?>(null)
    val hoveredContainerId: StateFlow<BlockId?> = _hoveredContainerId

    private val _detachingFromContainerId = MutableStateFlow<BlockId?>(null)
    val detachingFromContainerId: StateFlow<BlockId?> = _detachingFromContainerId

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

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

    val isLockedBySelf: StateFlow<Boolean> = _lockState.map { state ->
        state is LockState.LockedByOther && currentUserId != null && state.info?.userId == currentUserId
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showForceUnlock: Boolean get() = canForceUnlock

    private var heartbeatJob: Job? = null

    // Auto-save
    private val autoSaveManager = AutoSaveManager(
        scope = viewModelScope,
        debounceMs = autoSaveDebounceMs,
        save = { performAutoSave() },
    )
    val autoSaveStatus: StateFlow<AutoSaveStatus> = autoSaveManager.status

    init {
        // Cancel auto-save when entering read-only mode
        viewModelScope.launch {
            isReadOnly.collect { readOnly ->
                if (readOnly) {
                    autoSaveManager.cancelPendingAutoSave()
                }
            }
        }
    }

    private suspend fun performAutoSave() {
        val p = _project.value ?: return
        val graphSnapshot = _graph.value
        val descSnapshot = p.description
        try {
            val updated = apiClient.updateProject(
                p.id,
                UpdateProjectRequest(
                    name = p.name,
                    dagGraph = graphSnapshot,
                    description = descSnapshot,
                ),
            )
            // Capture current description before overwriting _project
            val currentDesc = _project.value?.description
            _project.value = updated
            // Only clear dirty if state hasn't changed during the save
            if (_graph.value == graphSnapshot && currentDesc == descSnapshot) {
                _isDirty.value = false
            }
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 409) {
                val conflict = e.parseLockConflict()
                if (conflict != null) {
                    _lockState.value = LockState.LockLost
                    heartbeatJob?.cancel()
                    autoSaveManager.cancelPendingAutoSave()
                }
            }
            throw e
        }
    }

    private fun scheduleAutoSave() {
        if (isReadOnly.value || !_isDirty.value || _isSaving.value) return
        autoSaveManager.scheduleAutoSave()
    }

    // External config discovery (TC build types, GH workflows, etc.)
    private val _teamConnections = MutableStateFlow<List<Connection>>(emptyList())
    val teamConnections: StateFlow<List<Connection>> = _teamConnections

    private val _externalConfigs = MutableStateFlow<Map<BlockId, List<ExternalConfig>>>(emptyMap())
    val externalConfigs: StateFlow<Map<BlockId, List<ExternalConfig>>> = _externalConfigs

    private val _isFetchingConfigs = MutableStateFlow<Set<BlockId>>(emptySet())
    val isFetchingConfigs: StateFlow<Set<BlockId>> = _isFetchingConfigs

    private val _configFetchError = MutableStateFlow<Map<BlockId, String?>>(emptyMap())
    val configFetchError: StateFlow<Map<BlockId, String?>> = _configFetchError

    private val _isFetchingConfigParams = MutableStateFlow<Set<BlockId>>(emptySet())
    val isFetchingConfigParams: StateFlow<Set<BlockId>> = _isFetchingConfigParams

    private val configFetchJobs = mutableMapOf<BlockId, Job>()
    private val paramsFetchJobs = mutableMapOf<BlockId, Job>()

    fun loadProject() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                reloadProjectAndAcquireLock()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
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
        revalidate()
        attemptAcquireLock()
        loadTeamConnections()
    }

    private fun loadTeamConnections() {
        val client = connectionApiClient ?: return
        viewModelScope.launch {
            try {
                val response = client.listConnections(limit = 200)
                _teamConnections.value = response.connections
            } catch (_: Exception) {
                // Silently fail — connection dropdown will be empty
            }
        }
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
                _error.value = e.toUiMessage()
            }
        }
    }

    fun retryAcquireLock() {
        viewModelScope.launch {
            try {
                reloadProjectAndAcquireLock()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun save() {
        if (isReadOnly.value) return
        autoSaveManager.cancelPendingAutoSave()
        flushPendingUndo()
        val p = _project.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                val updated = apiClient.updateProject(
                    p.id,
                    UpdateProjectRequest(
                        name = p.name,
                        dagGraph = _graph.value,
                        description = p.description,
                    ),
                )
                _project.value = updated
                _isDirty.value = false
                autoSaveManager.resetRetryCounter()
                autoSaveManager.setStatus(AutoSaveStatus.Saved)
            } catch (e: ClientRequestException) {
                if (e.response.status.value == 409) {
                    val conflict = e.parseLockConflict()
                    if (conflict != null) {
                        _lockState.value = LockState.LockLost
                        heartbeatJob?.cancel()
                        autoSaveManager.cancelPendingAutoSave()
                    }
                }
                _error.value = e.toUiMessage()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
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
                _error.value = UiMessage.LockReacquireFailed(e.message)
            } catch (e: Exception) {
                _lockState.value = LockState.LockLost
                _error.value = UiMessage.LockReacquireFailed(e.message ?: "")
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
        val position = BlockPosition(x, y, BlockPosition.DEFAULT_CONTAINER_WIDTH, BlockPosition.DEFAULT_CONTAINER_HEIGHT, BlockPosition.DEFAULT_NEW_CONTAINER_HEADER_HEIGHT)
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

        // Remove from top-level
        var newBlocks = g.blocks.filter { it.id !in blockIds }
        val newEdges = g.edges.filter { it.fromBlockId !in blockIds && it.toBlockId !in blockIds }
        val newPositions = g.positions.filterKeys { it !in blockIds }

        // Also remove from container children
        newBlocks = newBlocks.map { block ->
            if (block is Block.ContainerBlock) {
                val childIdsToRemove = block.children.blocks.filter { it.id in blockIds }.map { it.id }.toSet()
                if (childIdsToRemove.isNotEmpty()) {
                    block.copy(children = block.children.copy(
                        blocks = block.children.blocks.filter { it.id !in childIdsToRemove },
                        edges = block.children.edges.filter { it.fromBlockId !in childIdsToRemove && it.toBlockId !in childIdsToRemove },
                        positions = block.children.positions.filterKeys { it !in childIdsToRemove },
                    ))
                } else block
            } else block
        }

        updateGraph(g.copy(blocks = newBlocks, edges = newEdges, positions = newPositions))
        // Clean up external config state for deleted blocks
        for (blockId in blockIds) {
            configFetchJobs.remove(blockId)?.cancel()
            paramsFetchJobs.remove(blockId)?.cancel()
        }
        _externalConfigs.value -= blockIds
        _configFetchError.value -= blockIds
        _isFetchingConfigs.value -= blockIds
        _isFetchingConfigParams.value -= blockIds
        _selectedBlockIds.value = emptySet()
        _selectedEdgeIndex.value = null
    }

    fun removeSelectedEdge() {
        if (isReadOnly.value) return
        val idx = _selectedEdgeIndex.value ?: return
        val containerId = _selectedEdgeContainerId.value
        val g = _graph.value

        if (containerId != null) {
            // Edge inside a container
            val container = g.blocks.find { it.id == containerId } as? Block.ContainerBlock ?: return
            if (idx in container.children.edges.indices) {
                val updatedEdges = container.children.edges.toMutableList().apply { removeAt(idx) }
                val updatedContainer = container.copy(children = container.children.copy(edges = updatedEdges))
                updateGraph(g.copy(blocks = g.blocks.map { if (it.id == containerId) updatedContainer else it }))
            }
        } else {
            // Top-level edge
            if (idx in g.edges.indices) {
                updateGraph(g.copy(edges = g.edges.toMutableList().apply { removeAt(idx) }))
            }
        }
        _selectedEdgeIndex.value = null
        _selectedEdgeContainerId.value = null
    }

    fun moveBlock(blockId: BlockId, dx: Float, dy: Float) {
        if (isReadOnly.value) return
        val g = _graph.value
        val parentId = _parentLookup.value[blockId]

        if (parentId != null) {
            // Child block: update position within container's children graph
            val container = g.blocks.find { it.id == parentId } as? Block.ContainerBlock ?: return
            val childPos = container.children.positions[blockId] ?: return
            val newChildPos = childPos.copy(x = childPos.x + dx, y = childPos.y + dy)
            val updatedChildren = container.children.copy(
                positions = container.children.positions + (blockId to newChildPos),
            )
            val updatedContainer = container.copy(children = updatedChildren)
            _graph.value = g.copy(
                blocks = g.blocks.map { if (it.id == parentId) updatedContainer else it },
            )
        } else {
            // Top-level block: update position in root graph
            val current = g.positions[blockId] ?: return
            val newPos = current.copy(x = current.x + dx, y = current.y + dy)
            _graph.value = g.copy(positions = g.positions + (blockId to newPos))
        }
        // Move without pushing to undo stack (would flood during drag)
        _isDirty.value = true
    }

    /** Update hover/detach feedback after all blocks in a drag have been moved.
     *  If ANY selected block passes the check, the feedback is set for ALL. */
    fun updateDragFeedback(movedBlockIds: Set<BlockId>) {
        val g = _graph.value

        // Check detach: if any child block center is outside the container's full bounds
        // (header area is still "inside" — children in the header don't trigger detach,
        // auto-resize will push them below the header on commit)
        var detachContainer: BlockId? = null
        for (blockId in movedBlockIds) {
            val parentId = _parentLookup.value[blockId] ?: continue
            val container = g.blocks.find { it.id == parentId } as? Block.ContainerBlock ?: continue
            val childPos = container.children.positions[blockId] ?: continue
            val containerPos = g.positions[parentId] ?: continue
            val absX = containerPos.x + childPos.x + childPos.width / 2
            val absY = containerPos.y + containerPos.headerHeight + childPos.y + childPos.height / 2
            val insideX = absX in containerPos.x..(containerPos.x + containerPos.width)
            val insideY = absY in containerPos.y..(containerPos.y + containerPos.height)
            if (!insideX || !insideY) {
                detachContainer = parentId
                break
            }
        }
        _detachingFromContainerId.value = detachContainer

        // Check hover: if any top-level non-container block is over a container, flag hover
        var hoverContainer: BlockId? = null
        for (blockId in movedBlockIds) {
            if (_parentLookup.value[blockId] != null) continue // skip children
            val block = g.blocks.find { it.id == blockId } ?: continue
            if (block is Block.ContainerBlock) continue
            val pos = g.positions[blockId] ?: continue
            val centerX = pos.x + pos.width / 2
            val centerY = pos.y + pos.height / 2
            val hovered = g.blocks.filterIsInstance<Block.ContainerBlock>().find { container ->
                val cPos = g.positions[container.id] ?: return@find false
                centerX in cPos.x..(cPos.x + cPos.width) &&
                    centerY in (cPos.y + cPos.headerHeight)..(cPos.y + cPos.height)
            }
            if (hovered != null) {
                hoverContainer = hovered.id
                break
            }
        }
        _hoveredContainerId.value = hoverContainer
    }

    /** Returns a user-facing message if edges were removed during container membership change, null otherwise. */
    fun commitMove(): String? {
        val draggedHover = _hoveredContainerId.value
        val draggedDetach = _detachingFromContainerId.value
        _hoveredContainerId.value = null
        _detachingFromContainerId.value = null

        var droppedEdgeCount = 0

        // Handle container membership changes (mutually exclusive: can't enter and leave at the same time)
        if (draggedDetach != null) {
            // Detach takes priority — push all matching children out at once (preserves relative positions)
            val selected = _selectedBlockIds.value
            val toDetach = selected.filter { _parentLookup.value[it] == draggedDetach }.toSet()
            for (blockId in toDetach) {
                droppedEdgeCount += countEdgesForBlock(blockId, inContainer = draggedDetach)
            }
            if (toDetach.isNotEmpty()) {
                removeBlocksFromContainer(toDetach, draggedDetach)
            }
        } else if (draggedHover != null) {
            // Top-level blocks being dragged into a container
            val selected = _selectedBlockIds.value
            for (blockId in selected) {
                val block = _graph.value.blocks.find { it.id == blockId }
                if (block != null && block !is Block.ContainerBlock && _parentLookup.value[blockId] == null) {
                    droppedEdgeCount += countEdgesForBlock(blockId, inContainer = null)
                    moveBlockIntoContainer(blockId, draggedHover)
                }
            }
        }

        // Auto-resize any container whose children extend beyond its bounds
        autoResizeAllContainers()

        pushUndoState(_graph.value)
        revalidate()
        scheduleAutoSave()

        return if (droppedEdgeCount > 0) {
            "$droppedEdgeCount connection${if (droppedEdgeCount > 1) "s" else ""} removed (cross-boundary)"
        } else null
    }

    /** Count edges connected to a block. If inContainer is non-null, counts within that container's edges. */
    private fun countEdgesForBlock(blockId: BlockId, inContainer: BlockId?): Int {
        val g = _graph.value
        return if (inContainer != null) {
            val container = g.blocks.find { it.id == inContainer } as? Block.ContainerBlock ?: return 0
            container.children.edges.count { it.fromBlockId == blockId || it.toBlockId == blockId }
        } else {
            g.edges.count { it.fromBlockId == blockId || it.toBlockId == blockId }
        }
    }

    private fun moveBlockIntoContainer(blockId: BlockId, containerId: BlockId) {
        val g = _graph.value
        val block = g.blocks.find { it.id == blockId } ?: return
        val blockPos = g.positions[blockId] ?: return
        val container = g.blocks.find { it.id == containerId } as? Block.ContainerBlock ?: return
        val containerPos = g.positions[containerId] ?: return

        // Convert absolute position to relative within container content area
        val relX = blockPos.x - containerPos.x
        val relY = blockPos.y - containerPos.y - containerPos.headerHeight
        val relPos = BlockPosition(relX, relY, blockPos.width, blockPos.height)

        // Remove from top-level
        val newBlocks = g.blocks.filter { it.id != blockId }
        val newPositions = g.positions - blockId
        val newEdges = g.edges.filter { it.fromBlockId != blockId && it.toBlockId != blockId }

        // Add to container children
        val updatedChildren = container.children.copy(
            blocks = container.children.blocks + block,
            positions = container.children.positions + (blockId to relPos),
        )
        val updatedContainer = container.copy(children = updatedChildren)
        val finalBlocks = newBlocks.map { if (it.id == containerId) updatedContainer else it }

        _graph.value = g.copy(blocks = finalBlocks, edges = newEdges, positions = newPositions)

        // Auto-resize container if needed
        autoResizeContainer(containerId)
    }

    /**
     * Remove block(s) from container and push them outward past the nearest container border.
     * When removing multiple blocks, they are all pushed by the same delta to preserve relative positions.
     */
    private fun removeBlocksFromContainer(blockIds: Set<BlockId>, containerId: BlockId) {
        val g = _graph.value
        val container = g.blocks.find { it.id == containerId } as? Block.ContainerBlock ?: return
        val containerPos = g.positions[containerId] ?: return

        // Compute absolute positions and find the centroid of removed blocks
        data class RemovedBlock(val block: Block, val absPos: BlockPosition)
        val removed = mutableListOf<RemovedBlock>()
        var sumCenterX = 0f; var sumCenterY = 0f
        for (blockId in blockIds) {
            val childPos = container.children.positions[blockId] ?: continue
            val block = container.children.blocks.find { it.id == blockId } ?: continue
            val absX = containerPos.x + childPos.x
            val absY = containerPos.y + containerPos.headerHeight + childPos.y
            val absPos = BlockPosition(absX, absY, childPos.width, childPos.height)
            removed.add(RemovedBlock(block, absPos))
            sumCenterX += absX + childPos.width / 2
            sumCenterY += absY + childPos.height / 2
        }
        if (removed.isEmpty()) return

        // Find bounding box of all removed blocks (absolute coords)
        var bbLeft = Float.MAX_VALUE; var bbTop = Float.MAX_VALUE
        var bbRight = Float.MIN_VALUE; var bbBottom = Float.MIN_VALUE
        for (rb in removed) {
            bbLeft = minOf(bbLeft, rb.absPos.x)
            bbTop = minOf(bbTop, rb.absPos.y)
            bbRight = maxOf(bbRight, rb.absPos.x + rb.absPos.width)
            bbBottom = maxOf(bbBottom, rb.absPos.y + rb.absPos.height)
        }
        val bbCenterX = (bbLeft + bbRight) / 2
        val bbCenterY = (bbTop + bbBottom) / 2

        // Distance from bounding box edges to container borders
        val cLeft = containerPos.x; val cTop = containerPos.y
        val cRight = containerPos.x + containerPos.width; val cBottom = containerPos.y + containerPos.height
        val distLeft = bbCenterX - cLeft
        val distRight = cRight - bbCenterX
        val distTop = bbCenterY - cTop
        val distBottom = cBottom - bbCenterY
        val minDist = minOf(distLeft, distRight, distTop, distBottom)
        val pushPadding = 20f

        // Push ALL blocks so the entire bounding box clears the nearest border
        val pushDx: Float
        val pushDy: Float
        when (minDist) {
            distLeft -> { pushDx = cLeft - bbRight - pushPadding; pushDy = 0f }
            distRight -> { pushDx = cRight - bbLeft + pushPadding; pushDy = 0f }
            distTop -> { pushDx = 0f; pushDy = cTop - bbBottom - pushPadding }
            else -> { pushDx = 0f; pushDy = cBottom - bbTop + pushPadding }
        }

        // Remove from container children
        val removedIds = blockIds
        val updatedChildren = container.children.copy(
            blocks = container.children.blocks.filter { it.id !in removedIds },
            positions = container.children.positions.filterKeys { it !in removedIds },
            edges = container.children.edges.filter { it.fromBlockId !in removedIds && it.toBlockId !in removedIds },
        )
        val updatedContainer = container.copy(children = updatedChildren)

        // Add to top-level with pushed positions
        var finalBlocks = g.blocks.map { if (it.id == containerId) updatedContainer else it }
        val finalPositions = g.positions.toMutableMap()
        for (rb in removed) {
            finalBlocks = finalBlocks + rb.block
            finalPositions[rb.block.id] = rb.absPos.copy(x = rb.absPos.x + pushDx, y = rb.absPos.y + pushDy)
        }

        _graph.value = g.copy(blocks = finalBlocks, positions = finalPositions)
    }

    /**
     * Auto-resize container to fit all children. Expands in all 4 directions as needed.
     * Does NOT push children — only grows the container.
     */
    private fun autoResizeContainer(containerId: BlockId) {
        val g = _graph.value
        val container = g.blocks.find { it.id == containerId } as? Block.ContainerBlock ?: return
        var cPos = g.positions[containerId] ?: return
        if (container.children.blocks.isEmpty()) return
        val padding = 10f

        // Find bounding box of all children (relative coords)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxRight = 0f; var maxBottom = 0f
        for (child in container.children.blocks) {
            val cp = container.children.positions[child.id] ?: continue
            minX = minOf(minX, cp.x)
            minY = minOf(minY, cp.y)
            maxRight = maxOf(maxRight, cp.x + cp.width)
            maxBottom = maxOf(maxBottom, cp.y + cp.height)
        }

        // Expand left/up if children have negative coords
        var updatedPositions: MutableMap<BlockId, BlockPosition>? = null
        if (minX < padding) {
            val shiftX = padding - minX
            updatedPositions = (updatedPositions ?: container.children.positions.toMutableMap())
            for (child in container.children.blocks) {
                val cp = updatedPositions[child.id] ?: continue
                updatedPositions[child.id] = cp.copy(x = cp.x + shiftX)
            }
            cPos = cPos.copy(x = cPos.x - shiftX, width = cPos.width + shiftX)
            maxRight += shiftX
        }
        if (minY < padding) {
            val shiftY = padding - minY
            updatedPositions = (updatedPositions ?: container.children.positions.toMutableMap())
            for (child in container.children.blocks) {
                val cp = updatedPositions[child.id] ?: continue
                updatedPositions[child.id] = cp.copy(y = cp.y + shiftY)
            }
            cPos = cPos.copy(y = cPos.y - shiftY, height = cPos.height + shiftY)
            maxBottom += shiftY
        }

        // Expand right/bottom
        val neededWidth = maxOf(cPos.width, maxRight + padding)
        val neededHeight = maxOf(cPos.height, maxBottom + cPos.headerHeight + padding)
        if (neededWidth > cPos.width || neededHeight > cPos.height) {
            cPos = cPos.copy(width = neededWidth, height = neededHeight)
        }

        // Apply
        val origPos = g.positions[containerId]
        if (updatedPositions != null || cPos != origPos) {
            val finalChildren = if (updatedPositions != null) {
                container.children.copy(positions = updatedPositions)
            } else container.children
            val updatedContainer = container.copy(children = finalChildren)
            _graph.value = _graph.value.copy(
                blocks = _graph.value.blocks.map { if (it.id == containerId) updatedContainer else it },
                positions = _graph.value.positions + (containerId to cPos),
            )
        }
    }

    /** Auto-resize all containers whose children extend beyond their bounds. */
    private fun autoResizeAllContainers() {
        for (block in _graph.value.blocks) {
            if (block is Block.ContainerBlock && block.children.blocks.isNotEmpty()) {
                autoResizeContainer(block.id)
            }
        }
    }

    fun resizeBlock(blockId: BlockId, edge: ResizeEdge, dx: Float, dy: Float) {
        if (isReadOnly.value) return
        val g = _graph.value
        val parentId = _parentLookup.value[blockId]

        if (parentId != null) {
            // Child block: resize within container children positions
            val container = g.blocks.find { it.id == parentId } as? Block.ContainerBlock ?: return
            val pos = container.children.positions[blockId] ?: return
            val newPos = computeResizedPosition(pos, edge, dx, dy, isContainer = false)
            val updatedChildren = container.children.copy(
                positions = container.children.positions + (blockId to newPos),
            )
            val updatedContainer = container.copy(children = updatedChildren)
            _graph.value = g.copy(blocks = g.blocks.map { if (it.id == parentId) updatedContainer else it })
        } else {
            val pos = g.positions[blockId] ?: return
            val block = g.blocks.find { it.id == blockId }

            if (block is Block.ContainerBlock && block.children.blocks.isNotEmpty()) {
                val padding = 10f
                val resizesLeft = edge in setOf(ResizeEdge.Left, ResizeEdge.TopLeft, ResizeEdge.BottomLeft)
                val resizesTop = edge in setOf(ResizeEdge.Top, ResizeEdge.TopLeft, ResizeEdge.TopRight)

                // Only shift children when the border is blocked by the content min.
                // Shift by exactly the resize delta, capped at the available gap.
                var updatedBlock = block
                val updatedPos = pos
                if (resizesLeft || resizesTop) {
                    val positions = updatedBlock.children.positions.toMutableMap()
                    if (resizesLeft && dx > 0f) {
                        // dx > 0 means dragging left edge rightward (shrinking)
                        val minChildX = updatedBlock.children.blocks.minOfOrNull { positions[it.id]?.x ?: Float.MAX_VALUE } ?: 0f
                        val gap = (minChildX - padding).coerceAtLeast(0f)
                        val shiftX = minOf(dx, gap) // only shift what the resize needs, up to available gap
                        if (shiftX > 0f) {
                            for (child in updatedBlock.children.blocks) {
                                val cp = positions[child.id] ?: continue
                                positions[child.id] = cp.copy(x = cp.x - shiftX)
                            }
                        }
                    }
                    if (resizesTop && dy > 0f) {
                        // dy > 0 means dragging top edge downward (shrinking)
                        val minChildY = updatedBlock.children.blocks.minOfOrNull { positions[it.id]?.y ?: Float.MAX_VALUE } ?: 0f
                        val gap = (minChildY - padding).coerceAtLeast(0f)
                        val shiftY = minOf(dy, gap)
                        if (shiftY > 0f) {
                            for (child in updatedBlock.children.blocks) {
                                val cp = positions[child.id] ?: continue
                                positions[child.id] = cp.copy(y = cp.y - shiftY)
                            }
                        }
                    }
                    updatedBlock = updatedBlock.copy(children = updatedBlock.children.copy(positions = positions))
                }

                // Compute content-fitting minimum from (possibly shifted) children
                val contentMinW = updatedBlock.children.blocks.maxOf { child ->
                    val cp = updatedBlock.children.positions[child.id] ?: return@maxOf 0f
                    cp.x + cp.width + padding
                }.coerceAtLeast(BlockPosition.DEFAULT_CONTAINER_WIDTH)
                val contentMinH = updatedBlock.children.blocks.maxOf { child ->
                    val cp = updatedBlock.children.positions[child.id] ?: return@maxOf 0f
                    cp.y + cp.height + updatedPos.headerHeight + padding
                }.coerceAtLeast(BlockPosition.DEFAULT_CONTAINER_HEIGHT)

                val newPos = computeResizedPosition(updatedPos, edge, dx, dy, isContainer = true, contentMinW, contentMinH)
                _graph.value = g.copy(
                    blocks = g.blocks.map { if (it.id == blockId) updatedBlock else it },
                    positions = g.positions + (blockId to newPos),
                )
            } else {
                val newPos = computeResizedPosition(pos, edge, dx, dy, isContainer = block is Block.ContainerBlock)
                _graph.value = g.copy(positions = g.positions + (blockId to newPos))
            }
        }
        _isDirty.value = true
    }

    private fun computeResizedPosition(
        pos: BlockPosition, edge: ResizeEdge, dx: Float, dy: Float, isContainer: Boolean,
        contentMinW: Float = 0f, contentMinH: Float = 0f,
    ): BlockPosition {
        val defaultMinW = if (isContainer) BlockPosition.DEFAULT_CONTAINER_WIDTH else BlockPosition.DEFAULT_BLOCK_WIDTH
        val defaultMinH = if (isContainer) maxOf(BlockPosition.DEFAULT_CONTAINER_HEIGHT, pos.headerHeight + BlockPosition.MIN_CONTAINER_CONTENT_HEIGHT) else BlockPosition.DEFAULT_BLOCK_HEIGHT
        val minW = maxOf(defaultMinW, contentMinW)
        val minH = maxOf(defaultMinH, contentMinH)

        val (newX, newWidth) = when (edge) {
            ResizeEdge.Left, ResizeEdge.TopLeft, ResizeEdge.BottomLeft -> {
                val proposedW = (pos.width - dx).coerceAtLeast(minW)
                val actualDx = pos.width - proposedW
                (pos.x + actualDx) to proposedW
            }
            ResizeEdge.Right, ResizeEdge.TopRight, ResizeEdge.BottomRight -> {
                pos.x to (pos.width + dx).coerceAtLeast(minW)
            }
            else -> pos.x to pos.width
        }

        val (newY, newHeight) = when (edge) {
            ResizeEdge.Top, ResizeEdge.TopLeft, ResizeEdge.TopRight -> {
                val proposedH = (pos.height - dy).coerceAtLeast(minH)
                val actualDy = pos.height - proposedH
                (pos.y + actualDy) to proposedH
            }
            ResizeEdge.Bottom, ResizeEdge.BottomLeft, ResizeEdge.BottomRight -> {
                pos.y to (pos.height + dy).coerceAtLeast(minH)
            }
            else -> pos.y to pos.height
        }

        return pos.copy(x = newX, y = newY, width = newWidth, height = newHeight)
    }

    fun resizeHeader(blockId: BlockId, dy: Float) {
        if (isReadOnly.value) return
        val g = _graph.value
        val pos = g.positions[blockId] ?: return
        val newHeaderHeight = (pos.headerHeight + dy).coerceIn(
            BlockPosition.MIN_CONTAINER_HEADER_HEIGHT,
            pos.height - BlockPosition.MIN_CONTAINER_CONTENT_HEIGHT,
        )
        _graph.value = g.copy(positions = g.positions + (blockId to pos.copy(headerHeight = newHeaderHeight)))
        _isDirty.value = true
        // Children positions are relative to header bottom — growing the header pushes them down
        autoResizeContainer(blockId)
    }

    fun commitResize() {
        autoResizeAllContainers()
        pushUndoState(_graph.value)
        revalidate()
        scheduleAutoSave()
    }

    /** Returns an error message if the edge was rejected, null on success. */
    fun addEdge(fromBlockId: BlockId, toBlockId: BlockId): String? {
        if (isReadOnly.value) return null
        if (fromBlockId == toBlockId) return null

        val fromParent = _parentLookup.value[fromBlockId]
        val toParent = _parentLookup.value[toBlockId]

        // Cross-boundary prevention: both blocks must be at the same nesting level
        if (fromParent != toParent) {
            return "Cannot connect blocks across container boundaries"
        }

        if (fromParent != null) {
            // Both are inside the same container — add edge to container's children graph
            val g = _graph.value
            val container = g.blocks.find { it.id == fromParent } as? Block.ContainerBlock ?: return null
            if (container.children.edges.any { it.fromBlockId == fromBlockId && it.toBlockId == toBlockId }) return null
            val updatedChildren = container.children.copy(
                edges = container.children.edges + Edge(fromBlockId, toBlockId),
            )
            val updatedContainer = container.copy(children = updatedChildren)
            updateGraph(g.copy(blocks = g.blocks.map { if (it.id == fromParent) updatedContainer else it }))
        } else {
            // Both are top-level
            val g = _graph.value
            if (g.edges.any { it.fromBlockId == fromBlockId && it.toBlockId == toBlockId }) return null
            updateGraph(g.copy(edges = g.edges + Edge(fromBlockId, toBlockId)))
        }
        return null
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

    fun selectEdge(index: Int?, containerId: BlockId? = null) {
        _selectedEdgeIndex.value = index
        _selectedEdgeContainerId.value = containerId
        _selectedBlockIds.value = emptySet()
    }

    // Project-level property updates (not tracked by undo — undo only covers graph changes).

    fun updateProjectName(name: String) {
        if (isReadOnly.value) return
        _project.value = _project.value?.copy(name = name)
        _isDirty.value = true
        scheduleAutoSave()
    }

    fun updateProjectDescription(description: String) {
        if (isReadOnly.value) return
        _project.value = _project.value?.copy(description = description)
        _isDirty.value = true
        scheduleAutoSave()
    }

    // Property updates — mutate graph without flooding undo stack.
    // Undo tracks structural changes (add/remove blocks/edges, moves).

    fun updateBlockName(blockId: BlockId, name: String) {
        updateBlockField(blockId) { block ->
            when (block) {
                is Block.ActionBlock -> block.copy(name = name)
                is Block.ContainerBlock -> block.copy(name = name)
            }
        }
    }

    fun updateBlockDescription(blockId: BlockId, description: String) {
        updateBlockField(blockId) { block ->
            when (block) {
                is Block.ActionBlock -> block.copy(description = description)
                is Block.ContainerBlock -> block.copy(description = description)
            }
        }
    }

    /** Transform a block by ID, searching both top-level and inside containers. */
    private fun updateBlockField(blockId: BlockId, transform: (Block) -> Block) {
        if (isReadOnly.value) return
        val g = _graph.value
        updateGraphSilent(g.copy(blocks = mapBlocksDeep(g.blocks, blockId, transform)))
    }

    /** Maps blocks, searching inside containers if needed. */
    private fun mapBlocksDeep(
        blocks: List<Block>, targetId: BlockId, transform: (Block) -> Block,
    ): List<Block> = blocks.map { block ->
        if (block.id == targetId) return@map transform(block)
        if (block is Block.ContainerBlock) {
            val hasChild = block.children.blocks.any { it.id == targetId }
            if (hasChild) {
                block.copy(children = block.children.copy(
                    blocks = block.children.blocks.map { child ->
                        if (child.id == targetId) transform(child) else child
                    }
                ))
            } else block
        } else block
    }

    fun updateBlockType(blockId: BlockId, type: BlockType) {
        if (isReadOnly.value) return
        val g = _graph.value
        updateGraph(g.copy(blocks = mapBlocksDeep(g.blocks, blockId) { block ->
            if (block is Block.ActionBlock) block.copy(type = type) else block
        }))
    }

    fun updateBlockParameters(blockId: BlockId, parameters: List<Parameter>) {
        updateActionBlock(blockId) { it.copy(parameters = parameters) }
    }

    fun updateBlockTimeout(blockId: BlockId, timeoutSeconds: Long?) {
        updateActionBlock(blockId) { it.copy(timeoutSeconds = timeoutSeconds) }
    }

    fun updateBlockInjectWebhookUrl(blockId: BlockId, inject: Boolean) {
        updateActionBlock(blockId) { it.copy(injectWebhookUrl = inject) }
    }

    fun updateBlockPreGate(blockId: BlockId, gate: Gate?) {
        updateActionBlock(blockId) { it.copy(preGate = gate) }
    }

    fun updateBlockPostGate(blockId: BlockId, gate: Gate?) {
        updateActionBlock(blockId) { it.copy(postGate = gate) }
    }

    fun updateBlockConnectionId(blockId: BlockId, connectionId: ConnectionId?) {
        updateActionBlock(blockId) { it.copy(connectionId = connectionId) }
        // Clear downstream state
        _externalConfigs.value -= blockId
        _configFetchError.value -= blockId
        configFetchJobs[blockId]?.cancel()
        paramsFetchJobs[blockId]?.cancel()
        // Clear config ID parameter and fetched params
        val block = findActionBlock(blockId) ?: return
        val configKey = block.type.configIdParameterKey()
        if (configKey != null) {
            val cleaned = block.parameters.filter { it.key != configKey }
            if (cleaned != block.parameters) {
                updateBlockParameters(blockId, cleaned)
            }
        }
        // Auto-fetch configs if connection set and block type supports it
        if (connectionId != null && configKey != null) {
            fetchExternalConfigs(blockId)
        }
    }

    fun fetchExternalConfigs(blockId: BlockId) {
        val client = connectionApiClient ?: return
        val block = findActionBlock(blockId) ?: return
        val connectionId = block.connectionId ?: return
        val connectionType = block.type.requiredConnectionType() ?: return

        configFetchJobs[blockId]?.cancel()
        configFetchJobs[blockId] = viewModelScope.launch {
            _isFetchingConfigs.value += blockId
            _configFetchError.value -= blockId
            try {
                val response = client.fetchExternalConfigs(connectionId, connectionType)
                _externalConfigs.value += (blockId to response.configs)
            } catch (e: Exception) {
                _configFetchError.value += (blockId to (e.message ?: "Failed to fetch"))
            } finally {
                _isFetchingConfigs.value -= blockId
            }
        }
    }

    fun selectExternalConfig(blockId: BlockId, configId: String) {
        val block = findActionBlock(blockId) ?: return
        val configKey = block.type.configIdParameterKey() ?: return

        // Set or update the config ID parameter
        val existing = block.parameters.toMutableList()
        val idx = existing.indexOfFirst { it.key == configKey }
        if (idx >= 0) {
            existing[idx] = existing[idx].copy(value = configId)
        } else {
            existing.add(0, Parameter(key = configKey, value = configId))
        }
        updateBlockParameters(blockId, existing)

        // Auto-fetch parameters for the selected config
        fetchExternalConfigParameters(blockId)
    }

    fun fetchExternalConfigParameters(blockId: BlockId) {
        val client = connectionApiClient ?: return
        val block = findActionBlock(blockId) ?: return
        val connectionId = block.connectionId ?: return
        val connectionType = block.type.requiredConnectionType() ?: return
        val configKey = block.type.configIdParameterKey() ?: return
        val configId = block.parameters.find { it.key == configKey }?.value
        if (configId.isNullOrBlank()) return

        paramsFetchJobs[blockId]?.cancel()
        paramsFetchJobs[blockId] = viewModelScope.launch {
            _isFetchingConfigParams.value += blockId
            try {
                val response = client.fetchExternalConfigParameters(connectionId, connectionType, configId)
                // Merge: update label/description for existing keys, add new keys, preserve user-only params
                val currentBlock = findActionBlock(blockId) ?: return@launch
                val fetchedByName = response.parameters.associateBy { it.name }
                // Preserve original order: update existing, keep user-only in place
                val updatedExisting = currentBlock.parameters.map { param ->
                    val ext = fetchedByName[param.key]
                    if (ext != null) {
                        param.copy(label = ext.label, description = ext.description)
                    } else {
                        param // user-only param, keep as-is
                    }
                }
                // Append truly new params (not in existing)
                val existingKeys = currentBlock.parameters.map { it.key }.toSet()
                val newParams = response.parameters
                    .filter { it.name !in existingKeys }
                    .map { it.toParameter() }
                val merged = updatedExisting + newParams
                if (merged != currentBlock.parameters) {
                    updateBlockParameters(blockId, merged)
                }
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isFetchingConfigParams.value -= blockId
            }
        }
    }

    private fun findActionBlock(blockId: BlockId): Block.ActionBlock? {
        // Search top-level
        _graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == blockId }?.let { return it }
        // Search inside containers
        for (block in _graph.value.blocks) {
            if (block is Block.ContainerBlock) {
                block.children.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == blockId }?.let { return it }
            }
        }
        return null
    }

    private fun updateActionBlock(blockId: BlockId, transform: (Block.ActionBlock) -> Block.ActionBlock) {
        if (isReadOnly.value) return
        val g = _graph.value
        updateGraphSilent(g.copy(blocks = mapBlocksDeep(g.blocks, blockId) { block ->
            if (block is Block.ActionBlock) transform(block) else block
        }))
    }

    fun undo() {
        if (isReadOnly.value) return
        flushPendingUndo()
        if (undoIndex <= 0) return
        undoIndex--
        _graph.value = undoStack[undoIndex]
        _isDirty.value = true
        updateUndoRedoState()
        revalidate()
        scheduleAutoSave()
    }

    fun redo() {
        if (isReadOnly.value) return
        flushPendingUndo()
        if (undoIndex >= undoStack.lastIndex) return
        undoIndex++
        _graph.value = undoStack[undoIndex]
        _isDirty.value = true
        updateUndoRedoState()
        revalidate()
        scheduleAutoSave()
    }

    fun copySelected() {
        val selected = _selectedBlockIds.value
        if (selected.isEmpty()) return
        val g = _graph.value

        // Collect blocks from both top-level and inside containers
        val blocks = mutableListOf<Block>()
        val edges = mutableListOf<Edge>()
        val positions = mutableMapOf<BlockId, BlockPosition>()

        // Top-level blocks
        for (block in g.blocks) {
            if (block.id in selected) {
                blocks.add(block)
                g.positions[block.id]?.let { positions[block.id] = it }
            }
            // Search inside containers for selected children
            if (block is Block.ContainerBlock) {
                val containerPos = g.positions[block.id]
                for (child in block.children.blocks) {
                    if (child.id in selected) {
                        blocks.add(child)
                        // Convert relative position to absolute for clipboard
                        val childPos = block.children.positions[child.id]
                        if (childPos != null && containerPos != null) {
                            positions[child.id] = childPos.copy(
                                x = containerPos.x + childPos.x,
                                y = containerPos.y + containerPos.headerHeight + childPos.y,
                            )
                        }
                    }
                }
                // Include container-internal edges between selected children
                for (edge in block.children.edges) {
                    if (edge.fromBlockId in selected && edge.toBlockId in selected) {
                        edges.add(edge)
                    }
                }
            }
        }
        // Top-level edges between selected blocks
        for (edge in g.edges) {
            if (edge.fromBlockId in selected && edge.toBlockId in selected) {
                edges.add(edge)
            }
        }

        _clipboard.value = DagGraph(blocks = blocks, edges = edges, positions = positions)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun pasteClipboard() {
        if (isReadOnly.value) return
        val clip = _clipboard.value ?: return
        val g = _graph.value

        val remapped = remapDagGraph(clip, emptyMap())
        // Offset positions to visually separate pasted blocks (preserve width/height)
        val offsetPositions = remapped.positions.mapValues { (_, pos) ->
            pos.copy(x = pos.x + 30f, y = pos.y + 30f)
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
        val allIds = buildSet {
            for (block in _graph.value.blocks) {
                add(block.id)
                if (block is Block.ContainerBlock) {
                    for (child in block.children.blocks) add(child.id)
                }
            }
        }
        _selectedBlockIds.value = allIds
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

    private var debouncedUndoJob: Job? = null
    private var pendingUndoGraph: DagGraph? = null

    private fun flushPendingUndo() {
        debouncedUndoJob?.cancel()
        pendingUndoGraph?.let { pushUndoState(it) }
        pendingUndoGraph = null
    }

    /** Structural change — pushes to undo stack. */
    private fun updateGraph(newGraph: DagGraph) {
        flushPendingUndo()
        _graph.value = newGraph
        _isDirty.value = true
        pushUndoState(newGraph)
        revalidate()
        scheduleAutoSave()
    }

    /** Property change — debounced push to undo stack (avoids flood on every keystroke). */
    private fun updateGraphSilent(newGraph: DagGraph) {
        _graph.value = newGraph
        _isDirty.value = true
        revalidate()
        pendingUndoGraph = newGraph
        debouncedUndoJob?.cancel()
        debouncedUndoJob = viewModelScope.launch {
            delay(500.milliseconds)
            pushUndoState(newGraph)
            pendingUndoGraph = null
        }
        scheduleAutoSave()
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
        rebuildParentLookup()
    }

    private fun rebuildParentLookup() {
        val lookup = mutableMapOf<BlockId, BlockId>()
        for (block in _graph.value.blocks) {
            if (block is Block.ContainerBlock) {
                for (child in block.children.blocks) {
                    lookup[child.id] = block.id
                }
            }
        }
        _parentLookup.value = lookup
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
