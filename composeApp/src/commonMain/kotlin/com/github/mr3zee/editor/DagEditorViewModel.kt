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

    private val _clipboard = MutableStateFlow<DagGraph?>(null)
    val clipboard: StateFlow<DagGraph?> = _clipboard

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
        scheduleAutoSave()
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

    // Project-level property updates (not tracked by undo — undo only covers graph changes).

    fun updateProjectDescription(description: String) {
        if (isReadOnly.value) return
        _project.value = _project.value?.copy(description = description)
        _isDirty.value = true
        scheduleAutoSave()
    }

    // Property updates — mutate graph without flooding undo stack.
    // Undo tracks structural changes (add/remove blocks/edges, moves).

    fun updateBlockName(blockId: BlockId, name: String) {
        // todo claude: duplicate 13 lines
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

    fun updateBlockDescription(blockId: BlockId, description: String) {
        // todo claude: duplicate 13 lines
        if (isReadOnly.value) return
        val g = _graph.value
        updateGraphSilent(
            g.copy(
                blocks = g.blocks.map { block ->
                    if (block.id != blockId) return@map block
                    when (block) {
                        is Block.ActionBlock -> block.copy(description = description)
                        is Block.ContainerBlock -> block.copy(description = description)
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
        return _graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == blockId }
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
