package com.github.mr3zee.execution

import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.api.withSequenceNumber
import com.github.mr3zee.connections.ConnectionsRepository
import com.github.mr3zee.dag.DagTopologicalSort
import com.github.mr3zee.dag.DagValidator
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleasesRepository
import com.github.mr3zee.template.TemplateEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Coroutine-based DAG execution engine.
 *
 * For each release, traverses the DAG in topological order, launching blocks
 * whose predecessors have all SUCCEEDED. Container blocks recursively execute
 * their sub-DAG. Blocks with pre/post gates suspend on CompletableDeferred until approved.
 */
class ExecutionEngine(
    private val repository: ReleasesRepository,
    private val blockExecutor: BlockExecutor,
    private val connectionsRepository: ConnectionsRepository,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(ExecutionEngine::class.java)

    // Active release jobs, keyed by release ID
    private val activeJobs = ConcurrentHashMap<ReleaseId, Job>()

    // Tracks whether completion events have been emitted for a release (prevents duplicates)
    private val completionEmitted = ConcurrentHashMap<ReleaseId, AtomicBoolean>()

    // Releases currently being restarted (cancel-and-recover in progress)
    private val restartingReleases = ConcurrentHashMap.newKeySet<ReleaseId>()

    // Releases currently being stopped (cancel-and-mark-stopped in progress)
    private val stoppingReleases = ConcurrentHashMap.newKeySet<ReleaseId>()

    // Per-release mutex to prevent concurrent restarts
    private val restartMutexes = ConcurrentHashMap<ReleaseId, Mutex>()

    // Pending gate approvals: releaseId -> blockId -> CompletableDeferred
    private val pendingApprovals = ConcurrentHashMap<ReleaseId, ConcurrentHashMap<BlockId, CompletableDeferred<Map<String, String>>>>()

    // Per-release sequence counter for replay support
    private val sequenceCounters = ConcurrentHashMap<ReleaseId, AtomicLong>()

    // Per-release replay buffer (most recent events, capped at maxReplayBufferSize)
    private val replayBuffers = ConcurrentHashMap<ReleaseId, java.util.ArrayDeque<ReleaseEvent>>()
    private val maxReplayBufferSize = 1000

    // Event stream for WebSocket subscribers
    private val _events = MutableSharedFlow<ReleaseEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ReleaseEvent> = _events.asSharedFlow()

    /**
     * Returns buffered events for [releaseId] with sequence number strictly greater than [afterSequence].
     * Returns `null` if the replay buffer has no record for this release (e.g. buffer was evicted
     * or the release never ran on this server instance), signalling that a full snapshot is needed.
     * Returns an empty list if the buffer exists but all events are at or below [afterSequence]
     * (i.e. the client is already up-to-date).
     */
    fun getReplayEvents(releaseId: ReleaseId, afterSequence: Long): List<ReleaseEvent>? {
        val buffer = replayBuffers[releaseId] ?: return null
        synchronized(buffer) {
            // If the client's last sequence is older than the oldest buffered event,
            // we cannot guarantee continuity — caller should fall back to a full snapshot.
            val oldest = buffer.peekFirst() ?: return emptyList()
            if (afterSequence > 0 && oldest.sequenceNumber > afterSequence + 1) {
                return null
            }
            return buffer.filter { it.sequenceNumber > afterSequence }
        }
    }

    private fun emitEvent(event: ReleaseEvent) {
        val releaseId = event.releaseId
        val seq = sequenceCounters.getOrPut(releaseId) { AtomicLong(0) }
            .incrementAndGet()
        val numberedEvent = event.withSequenceNumber(seq)

        val buffer = replayBuffers.getOrPut(releaseId) { java.util.ArrayDeque() }
        synchronized(buffer) {
            buffer.addLast(numberedEvent)
            while (buffer.size > maxReplayBufferSize) {
                buffer.removeFirst()
            }
        }

        _events.tryEmit(numberedEvent)
    }

    /**
     * Emit release completion events exactly once per release.
     * Both [cancelExecution] and [executeRelease]'s catch block can trigger completion;
     * the AtomicBoolean ensures only the first caller emits events.
     */
    private fun emitCompletionOnce(releaseId: ReleaseId, status: ReleaseStatus, finishedAt: Instant) {
        val flag = completionEmitted.getOrPut(releaseId) { AtomicBoolean(false) }
        if (flag.compareAndSet(false, true)) {
            emitEvent(ReleaseEvent.ReleaseStatusChanged(releaseId, status, finishedAt = finishedAt))
            emitEvent(ReleaseEvent.ReleaseCompleted(releaseId, status, finishedAt = finishedAt))
        }
    }

    fun startExecution(release: Release): Job {
        log.info("Starting execution for release {}", release.id.value)
        completionEmitted[release.id] = AtomicBoolean(false)
        // EXEC-C2: Use LAZY start to register the job BEFORE the coroutine can execute,
        // preventing ghost executions if cancel is called between launch and registerJob.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            executeRelease(release)
        }
        registerJob(release.id, job)
        job.start()
        return job
    }

    suspend fun awaitExecution(releaseId: ReleaseId) {
        activeJobs[releaseId]?.join()
    }

    suspend fun cancelExecution(releaseId: ReleaseId) {
        val mutex = restartMutexes.getOrPut(releaseId) { Mutex() }
        mutex.withLock {
            val job = activeJobs[releaseId]
            if (job != null) {
                pendingApprovals[releaseId]?.values?.forEach {
                    it.completeExceptionally(CancellationException("Release cancelled"))
                }
                job.cancel()
                job.join()
            }
            // Ensure CANCELLED is written even if the job was cancelled before executeRelease ran,
            // or if the release was STOPPED (no active job)
            val release = repository.findById(releaseId)
            if (release != null && release.status != ReleaseStatus.CANCELLED) {
                // Mark any non-terminal blocks as FAILED
                val executions = repository.findBlockExecutions(releaseId)
                for (exec in executions) {
                    if (exec.status == BlockStatus.STOPPED || exec.status == BlockStatus.RUNNING ||
                        exec.status == BlockStatus.WAITING || exec.status == BlockStatus.WAITING_FOR_INPUT
                    ) {
                        persistAndEmit(releaseId, exec.copy(
                            status = BlockStatus.FAILED,
                            error = "Release cancelled",
                            finishedAt = exec.finishedAt ?: Clock.System.now(),
                        ))
                    }
                }
                repository.setFinished(releaseId, ReleaseStatus.CANCELLED)
                emitCompletionOnce(releaseId, ReleaseStatus.CANCELLED, Clock.System.now())
            }
        }
    }

    fun approveBlock(releaseId: ReleaseId, blockId: BlockId, input: Map<String, String>): Boolean {
        val approvalMap = pendingApprovals[releaseId] ?: return false
        val deferred = approvalMap[blockId] ?: return false
        deferred.complete(input)
        return true
    }

    /**
     * Emit a block execution update event (e.g. for partial approval tracking).
     */
    internal fun emitBlockUpdate(releaseId: ReleaseId, execution: BlockExecution) {
        // Filter internal outputs (keys starting with '_') from WebSocket broadcasts
        val filtered = if (execution.outputs.any { it.key.startsWith("_") }) {
            execution.copy(outputs = execution.outputs.filterKeys { !it.startsWith("_") })
        } else {
            execution
        }
        emitEvent(ReleaseEvent.BlockExecutionUpdated(releaseId, filtered))
    }

    /**
     * Resume a previously RUNNING release after server restart.
     * Reconstructs the execution state from DB records and resumes blocks
     * based on their persisted status.
     */
    fun recoverRelease(release: Release, persistedExecutions: List<BlockExecution>) {
        completionEmitted[release.id] = AtomicBoolean(false)
        // EXEC-C2: Use LAZY start to register the job before execution begins
        val job = scope.launch(start = CoroutineStart.LAZY) {
            executeRecovery(release, persistedExecutions)
        }
        registerJob(release.id, job)
        job.start()
    }

    private suspend fun executeRecovery(release: Release, persistedExecutions: List<BlockExecution>) {
        executeWithReleaseErrorHandling(release) {
            // If recovering a PENDING release, transition it to RUNNING first
            if (release.status == ReleaseStatus.PENDING) {
                repository.setStarted(release.id)
            }

            val graph = release.dagSnapshot
            val sorted = DagTopologicalSort.sort(graph)
                ?: throw IllegalStateException("DAG contains a cycle")

            val predecessors = buildPredecessorMap(graph)
            val statusMap = ConcurrentHashMap<BlockId, BlockStatus>()
            val outputsMap = ConcurrentHashMap<BlockId, Map<String, String>>()

            // Rebuild status/outputs from persisted executions
            val execMap = persistedExecutions.associateBy { it.blockId }
            initStatusFromPersisted(graph.blocks, execMap, statusMap, outputsMap)

            val startedAt = release.startedAt ?: Clock.System.now()

            // Re-emit current state so WebSocket subscribers see the recovered state
            emitEvent(ReleaseEvent.ReleaseStatusChanged(release.id, ReleaseStatus.RUNNING, startedAt = startedAt))

            // Skip already SUCCEEDED blocks
            val remaining = sorted.filter { statusMap[it] != BlockStatus.SUCCEEDED }.toMutableList()

            coroutineScope {
                runWaveLoop(this, release, graph, remaining, predecessors, statusMap) { block ->
                    recoverBlock(release, block, statusMap, outputsMap, execMap, persistedExecutions)
                }
            }

            val allSucceeded = statusMap.values.all { it == BlockStatus.SUCCEEDED }
            val finalStatus = if (allSucceeded) ReleaseStatus.SUCCEEDED else ReleaseStatus.FAILED
            repository.setFinished(release.id, finalStatus)
            emitCompletionOnce(release.id, finalStatus, Clock.System.now())
        }
    }

    private suspend fun recoverBlock(
        release: Release,
        block: Block,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        execMap: Map<BlockId, BlockExecution>,
        persistedExecutions: List<BlockExecution>,
    ) {
        when (block) {
            is Block.ContainerBlock -> recoverContainer(release, block, statusMap, outputsMap, persistedExecutions)
            is Block.ActionBlock -> recoverAction(release, block, statusMap, outputsMap, execMap[block.id])
        }
    }

    private suspend fun recoverContainer(
        release: Release,
        container: Block.ContainerBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        persistedExecutions: List<BlockExecution>,
    ) {
        // Reuse the already-loaded executions from the parent recovery instead of re-fetching from DB
        val childExecMap = persistedExecutions.associateBy { it.blockId }

        runContainer(
            release = release,
            container = container,
            statusMap = statusMap,
            outputsMap = outputsMap,
            initChildren = { childGraph, childStatusMap, childOutputsMap ->
                initStatusFromPersisted(childGraph.blocks, childExecMap, childStatusMap, childOutputsMap)
            },
            runWaves = { wavesScope, childGraph, sorted, childPredecessors, childStatusMap, childOutputsMap ->
                val remaining = sorted.filter { childStatusMap[it] != BlockStatus.SUCCEEDED }.toMutableList()
                runWaveLoop(wavesScope, release, childGraph, remaining, childPredecessors, childStatusMap) { block ->
                    recoverBlock(release, block, childStatusMap, childOutputsMap, childExecMap, persistedExecutions)
                }
            },
        )
    }

    private suspend fun recoverAction(
        release: Release,
        block: Block.ActionBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        persistedExec: BlockExecution?,
    ) {
        when (persistedExec?.status) {
            BlockStatus.SUCCEEDED -> {
                // Already done -- use stored outputs
                outputsMap[block.id] = persistedExec.outputs
                statusMap[block.id] = BlockStatus.SUCCEEDED
            }
            BlockStatus.RUNNING -> {
                // Was running when server died -- call resume(), then check for post-gate
                resumeAction(release, block, statusMap, outputsMap)
            }
            BlockStatus.WAITING_FOR_INPUT -> {
                val startTime = persistedExec.startedAt ?: Clock.System.now()
                statusMap[block.id] = BlockStatus.WAITING_FOR_INPUT
                emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, persistedExec))

                // Resolve the gate and check if approvals already met threshold before crash
                val gatePhase = persistedExec.gatePhase
                    ?: error("Block ${block.id.value} has WAITING_FOR_INPUT status but no gatePhase recorded")
                val gate = when (gatePhase) {
                    GatePhase.PRE -> block.preGate
                    GatePhase.POST -> block.postGate
                }

                val deferred = registerDeferred(release.id, block.id)
                val rule = gate?.approvalRule
                if (rule != null && rule.requiredCount > 0 && persistedExec.approvals.size >= rule.requiredCount) {
                    deferred.complete(emptyMap())
                }
                try {
                    deferred.await()
                } finally {
                    pendingApprovals[release.id]?.remove(block.id)
                }

                when (gatePhase) {
                    GatePhase.PRE -> {
                        runBlockWithPostGate(release, block, startTime, statusMap, outputsMap, BlockExecutor::execute)
                    }
                    GatePhase.POST -> {
                        // Post-gate resolved — complete with stored outputs
                        completeBlockSuccess(release.id, block.id, persistedExec.outputs, startTime, statusMap, outputsMap)
                    }
                }
            }
            BlockStatus.FAILED -> {
                // Already failed -- keep it
                statusMap[block.id] = BlockStatus.FAILED
            }
            BlockStatus.STOPPED -> {
                // Stopped blocks stay stopped -- resumeRelease resets them to WAITING first
                statusMap[block.id] = BlockStatus.STOPPED
            }
            else -> {
                // WAITING or null -- execute normally
                executeAction(release, block, statusMap, outputsMap)
            }
        }
    }

    private suspend fun resumeAction(
        release: Release,
        block: Block.ActionBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        runBlockWithPostGate(release, block, Clock.System.now(), statusMap, outputsMap, BlockExecutor::resume)
    }

    /**
     * Restart a failed block within a release.
     *
     * Cancels the active execution (if any), resets the block and its
     * transitive dependents to WAITING, then re-launches via recovery.
     * A per-release mutex prevents concurrent restart calls from racing.
     */
    suspend fun restartBlock(releaseId: ReleaseId, blockId: BlockId): Boolean {
        val mutex = restartMutexes.getOrPut(releaseId) { Mutex() }
        return mutex.withLock {
            val release = repository.findById(releaseId) ?: return@withLock false

            // Cancel current execution FIRST, before modifying DB state,
            // so the running job doesn't see partially-reset blocks
            val activeJob = activeJobs[releaseId]
            if (activeJob != null && activeJob.isActive) {
                restartingReleases.add(releaseId)
                try {
                    activeJob.cancel()
                    activeJob.join()
                    // invokeOnCompletion has run and skipped cleanup due to restart flag
                } finally {
                    // Always clear the restart flag to prevent state leak on failure
                    restartingReleases.remove(releaseId)
                }
            }

            // Clean up stale in-memory state from the old job
            activeJobs.remove(releaseId)
            pendingApprovals.remove(releaseId)
            completionEmitted.remove(releaseId)
            replayBuffers.remove(releaseId)
            sequenceCounters.remove(releaseId)

            // Reset the failed block in DB
            val resetExec = BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.WAITING,
            )
            persistAndEmit(releaseId, resetExec)

            // Reset transitive dependents that were skipped due to the failure
            val dependents = findTransitiveDependents(release.dagSnapshot, blockId)
            for (depId in dependents) {
                val exec = repository.findBlockExecution(releaseId, depId)
                if (exec != null && exec.status == BlockStatus.FAILED) {
                    val depReset = exec.copy(
                        status = BlockStatus.WAITING,
                        error = null,
                        startedAt = null,
                        finishedAt = null,
                    )
                    persistAndEmit(releaseId, depReset)
                }
            }

            // Ensure release is in RUNNING state
            repository.updateStatus(releaseId, ReleaseStatus.RUNNING)

            // Re-launch via recovery
            val updatedRelease = repository.findById(releaseId)
                ?: error("Release $releaseId not found after status update")
            val executions = repository.findBlockExecutions(releaseId)
            recoverRelease(updatedRelease, executions)

            true
        }
    }

    /**
     * Stop a specific block within a running release.
     * This pauses the entire release: all running blocks get STOPPED and external builds are cancelled.
     */
    suspend fun stopBlock(releaseId: ReleaseId, @Suppress("UNUSED_PARAMETER") blockId: BlockId): Boolean {
        // blockId is validated by the service layer; the engine stops the entire release
        return stopReleaseInternal(releaseId)
    }

    /**
     * Stop an entire running release: all running blocks get STOPPED and external builds are cancelled.
     */
    suspend fun stopRelease(releaseId: ReleaseId): Boolean {
        return stopReleaseInternal(releaseId)
    }

    /**
     * Internal helper shared by [stopBlock] and [stopRelease].
     * Cancels the release job, marks all active blocks as STOPPED (using targeted DB update),
     * cancels external builds, and sets release to STOPPED.
     */
    private suspend fun stopReleaseInternal(releaseId: ReleaseId): Boolean {
        val mutex = restartMutexes.getOrPut(releaseId) { Mutex() }
        return mutex.withLock {
            val release = repository.findById(releaseId) ?: return@withLock false

            try {
                // Cancel the release-level coroutine job
                val activeJob = activeJobs[releaseId]
                if (activeJob != null && activeJob.isActive) {
                    stoppingReleases.add(releaseId)
                    activeJob.cancel()
                    activeJob.join()
                }
            } finally {
                stoppingReleases.remove(releaseId)
            }

            // Clean up stale in-memory state
            activeJobs.remove(releaseId)
            pendingApprovals.remove(releaseId)
            completionEmitted.remove(releaseId)
            replayBuffers.remove(releaseId)
            sequenceCounters.remove(releaseId)

            // Find all RUNNING/WAITING_FOR_INPUT blocks to stop
            val executions = repository.findBlockExecutions(releaseId)
            val blocksToStop = executions.filter {
                it.status == BlockStatus.RUNNING || it.status == BlockStatus.WAITING_FOR_INPUT
            }

            // Cancel external builds for running blocks (best-effort)
            for (exec in blocksToStop) {
                if (exec.status == BlockStatus.RUNNING) {
                    cancelExternalBuild(release, exec.blockId)
                }
            }

            // Batch-update blocks to STOPPED and release to STOPPED in a single transaction
            if (blocksToStop.isNotEmpty()) {
                val blockIds = blocksToStop.map { it.blockId }.toSet()
                repository.batchStopBlocks(releaseId, blockIds, Clock.System.now())
            } else {
                repository.updateStatus(releaseId, ReleaseStatus.STOPPED)
            }

            // Emit WebSocket events for each stopped block + release status change
            val updatedExecutions = repository.findBlockExecutions(releaseId)
            for (exec in updatedExecutions) {
                if (exec.status == BlockStatus.STOPPED && blocksToStop.any { it.blockId == exec.blockId }) {
                    emitBlockUpdate(releaseId, exec)
                }
            }
            emitEvent(ReleaseEvent.ReleaseStatusChanged(releaseId, ReleaseStatus.STOPPED))

            log.info("Release {} stopped ({} blocks stopped)", releaseId.value, blocksToStop.size)
            true
        }
    }

    /**
     * Resume a stopped release: resets all STOPPED blocks to WAITING and re-launches execution.
     */
    suspend fun resumeRelease(releaseId: ReleaseId): Boolean {
        val mutex = restartMutexes.getOrPut(releaseId) { Mutex() }
        return mutex.withLock {
            val release = repository.findById(releaseId) ?: return@withLock false
            if (release.status != ReleaseStatus.STOPPED) return@withLock false

            // Find all STOPPED blocks
            val executions = repository.findBlockExecutions(releaseId)
            val stoppedBlocks = executions.filter { it.status == BlockStatus.STOPPED }

            // Reset STOPPED blocks to WAITING and release to RUNNING in a single transaction
            if (stoppedBlocks.isNotEmpty()) {
                val blockIds = stoppedBlocks.map { it.blockId }.toSet()
                repository.batchResumeBlocks(releaseId, blockIds)
            } else {
                repository.updateStatus(releaseId, ReleaseStatus.RUNNING)
            }

            // Emit block updates for reset blocks
            val updatedExecutions = repository.findBlockExecutions(releaseId)
            for (exec in updatedExecutions) {
                if (stoppedBlocks.any { it.blockId == exec.blockId }) {
                    emitBlockUpdate(releaseId, exec)
                }
            }

            // Re-launch via recovery
            val updatedRelease = repository.findById(releaseId)
                ?: error("Release $releaseId not found after status update")
            recoverRelease(updatedRelease, updatedExecutions)

            log.info("Release {} resumed ({} blocks re-queued)", releaseId.value, stoppedBlocks.size)
            true
        }
    }

    /**
     * Cancel external build for a specific block (best-effort).
     */
    private suspend fun cancelExternalBuild(release: Release, blockId: BlockId) {
        val block = release.dagSnapshot.findActionBlock(blockId) ?: return
        val exec = repository.findBlockExecution(release.id, blockId) ?: return

        try {
            val connections = mutableMapOf<ConnectionId, ConnectionConfig>()
            block.connectionId?.let { connId ->
                val conn = connectionsRepository.findById(connId)
                if (conn != null) connections[connId] = conn.config
            }
            val context = ExecutionContext(
                releaseId = release.id,
                parameters = release.parameters,
                blockOutputs = mapOf(blockId to exec.outputs),
                connections = connections,
            )
            blockExecutor.cancel(block, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Failed to cancel external build for block {}: {}", blockId.value, e.message)
        }
    }

    /**
     * Find all blocks transitively reachable from [blockId] via outgoing edges.
     */
    private fun findTransitiveDependents(graph: DagGraph, blockId: BlockId): Set<BlockId> {
        val adjacency = mutableMapOf<BlockId, MutableSet<BlockId>>()
        for (edge in graph.edges) {
            adjacency.getOrPut(edge.fromBlockId) { mutableSetOf() }.add(edge.toBlockId)
        }
        val result = mutableSetOf<BlockId>()
        val queue = ArrayDeque<BlockId>()
        adjacency[blockId]?.let { queue.addAll(it) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (result.add(current)) {
                adjacency[current]?.let { queue.addAll(it) }
            }
        }
        return result
    }

    private suspend fun executeRelease(release: Release) {
        executeWithReleaseErrorHandling(release) {
            // Validate the DAG before execution
            val graph = release.dagSnapshot
            val errors = DagValidator.validate(graph)
            if (errors.isNotEmpty()) {
                repository.setFinished(release.id, ReleaseStatus.FAILED)
                emitCompletionOnce(release.id, ReleaseStatus.FAILED, Clock.System.now())
                return@executeWithReleaseErrorHandling
            }

            repository.setStarted(release.id)
            val startedAt = Clock.System.now()
            emitEvent(ReleaseEvent.ReleaseStatusChanged(release.id, ReleaseStatus.RUNNING, startedAt = startedAt))

            val sorted = DagTopologicalSort.sort(graph)
                ?: throw IllegalStateException("DAG contains a cycle")

            val predecessors = buildPredecessorMap(graph)
            val statusMap = ConcurrentHashMap<BlockId, BlockStatus>()
            val outputsMap = ConcurrentHashMap<BlockId, Map<String, String>>()

            for (block in graph.blocks) {
                statusMap[block.id] = BlockStatus.WAITING
            }

            // Use coroutineScope for structured concurrency -- cancellation propagates to all children
            val remaining = sorted.toMutableList()
            coroutineScope {
                runWaveLoop(this, release, graph, remaining, predecessors, statusMap) { block ->
                    executeBlock(release, block, statusMap, outputsMap)
                }
            }

            val allSucceeded = statusMap.values.all { it == BlockStatus.SUCCEEDED }
            val finalStatus = if (allSucceeded) ReleaseStatus.SUCCEEDED else ReleaseStatus.FAILED
            repository.setFinished(release.id, finalStatus)
            log.info("Release {} completed with status {}", release.id.value, finalStatus)
            emitCompletionOnce(release.id, finalStatus, Clock.System.now())
        }
    }

    private suspend fun executeBlock(
        release: Release,
        block: Block,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        when (block) {
            is Block.ContainerBlock -> executeContainer(release, block, statusMap, outputsMap)
            is Block.ActionBlock -> executeAction(release, block, statusMap, outputsMap)
        }
    }

    /**
     * Shared container execution logic used by both [executeContainer] and [recoverContainer].
     *
     * @param initChildren populates child status and output maps from either fresh WAITING
     *                      state or persisted DB records.
     * @param runWaves executes child waves using either fresh or recovery logic.
     */
    private suspend fun runContainer(
        release: Release,
        container: Block.ContainerBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        initChildren: suspend (DagGraph, MutableMap<BlockId, BlockStatus>, MutableMap<BlockId, Map<String, String>>) -> Unit,
        runWaves: suspend (CoroutineScope, DagGraph, List<BlockId>, Map<BlockId, Set<BlockId>>, MutableMap<BlockId, BlockStatus>, MutableMap<BlockId, Map<String, String>>) -> Unit,
    ) {
        val startTime = Clock.System.now()
        statusMap[container.id] = BlockStatus.RUNNING
        persistAndEmit(release.id, BlockExecution(
            blockId = container.id,
            releaseId = release.id,
            status = BlockStatus.RUNNING,
            startedAt = startTime,
        ))

        try {
            val childGraph = container.children
            if (childGraph.blocks.isEmpty()) {
                statusMap[container.id] = BlockStatus.SUCCEEDED
                persistAndEmit(release.id, BlockExecution(
                    blockId = container.id,
                    releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    startedAt = startTime,
                    finishedAt = Clock.System.now(),
                ))
                return
            }

            val sorted = DagTopologicalSort.sort(childGraph)
                ?: throw IllegalStateException("Container sub-DAG contains a cycle")

            val childPredecessors = buildPredecessorMap(childGraph)
            val childStatusMap = ConcurrentHashMap<BlockId, BlockStatus>()
            val childOutputsMap = ConcurrentHashMap<BlockId, Map<String, String>>()

            initChildren(childGraph, childStatusMap, childOutputsMap)

            coroutineScope {
                runWaves(this, childGraph, sorted, childPredecessors, childStatusMap, childOutputsMap)
            }

            // Propagate child outputs to parent scope
            outputsMap.putAll(childOutputsMap)
            statusMap.putAll(childStatusMap)

            val allChildrenSucceeded = childStatusMap.values.all { it == BlockStatus.SUCCEEDED }
            val containerStatus = if (allChildrenSucceeded) BlockStatus.SUCCEEDED else BlockStatus.FAILED

            statusMap[container.id] = containerStatus
            persistAndEmit(release.id, BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = containerStatus,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            statusMap[container.id] = BlockStatus.FAILED
            persistAndEmit(release.id, BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = e.message,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            ))
        }
    }

    private suspend fun executeContainer(
        release: Release,
        container: Block.ContainerBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        runContainer(
            release = release,
            container = container,
            statusMap = statusMap,
            outputsMap = outputsMap,
            initChildren = { childGraph, childStatusMap, _ ->
                for (child in childGraph.blocks) {
                    childStatusMap[child.id] = BlockStatus.WAITING
                    repository.upsertBlockExecution(
                        BlockExecution(
                            blockId = child.id,
                            releaseId = release.id,
                            status = BlockStatus.WAITING,
                        )
                    )
                }
            },
            runWaves = { wavesScope, childGraph, sorted, childPredecessors, childStatusMap, childOutputsMap ->
                val remaining = sorted.toMutableList()
                runWaveLoop(wavesScope, release, childGraph, remaining, childPredecessors, childStatusMap) { block ->
                    executeBlock(release, block, childStatusMap, childOutputsMap)
                }
            },
        )
    }

    private suspend fun executeAction(
        release: Release,
        block: Block.ActionBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        val startTime = Clock.System.now()

        val preGate = block.preGate

        executeWithBlockErrorHandling(release.id, block.id, startTime, statusMap, outputsMap) {
            // 1. Pre-gate (if configured)
            if (preGate != null) {
                val msg = resolveGateMessage(preGate, block.name, GatePhase.PRE, release, outputsMap)
                statusMap[block.id] = BlockStatus.WAITING_FOR_INPUT
                persistAndEmit(release.id, BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.WAITING_FOR_INPUT,
                    startedAt = startTime,
                    gatePhase = GatePhase.PRE,
                    gateMessage = msg,
                ))

                awaitGateApproval(release.id, block.id)

                // Transition to RUNNING, clear gate state and approvals
                statusMap[block.id] = BlockStatus.RUNNING
                persistAndEmit(release.id, BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.RUNNING,
                    startedAt = startTime,
                ))
            } else {
                statusMap[block.id] = BlockStatus.RUNNING
                persistAndEmit(release.id, BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.RUNNING,
                    startedAt = startTime,
                ))
            }

            // 2. Execute the block + 3. Post-gate (if configured)
            executeAndHandlePostGate(release, block, startTime, statusMap, outputsMap, BlockExecutor::execute)
        }
    }

    /**
     * Execute a block and handle post-gate if configured.
     * Shared by fresh execution, recovery (pre-gate resolved), and resume paths.
     */
    private suspend fun executeAndHandlePostGate(
        release: Release,
        block: Block.ActionBlock,
        startTime: Instant,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        run: suspend BlockExecutor.(Block.ActionBlock, List<Parameter>, ExecutionContext, ExecutionScope?) -> Map<String, String>,
    ): Map<String, String> {
        val outputs = resolveAndExecute(release, block, outputsMap, run)
        val postGate = block.postGate
        if (postGate != null) {
            outputsMap[block.id] = outputs
            val msg = resolveGateMessage(postGate, block.name, GatePhase.POST, release, outputsMap)
            statusMap[block.id] = BlockStatus.WAITING_FOR_INPUT
            persistAndEmit(release.id, BlockExecution(
                blockId = block.id,
                releaseId = release.id,
                status = BlockStatus.WAITING_FOR_INPUT,
                outputs = outputs,
                startedAt = startTime,
                gatePhase = GatePhase.POST,
                gateMessage = msg,
            ))
            awaitGateApproval(release.id, block.id)
        }
        return outputs
    }

    /**
     * Set block to RUNNING, persist, then execute with post-gate handling.
     * Shared by recovery (pre-gate resolved) and resume paths.
     */
    private suspend fun runBlockWithPostGate(
        release: Release,
        block: Block.ActionBlock,
        startTime: Instant,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        run: suspend BlockExecutor.(Block.ActionBlock, List<Parameter>, ExecutionContext, ExecutionScope?) -> Map<String, String>,
    ) {
        statusMap[block.id] = BlockStatus.RUNNING
        persistAndEmit(release.id, BlockExecution(
            blockId = block.id,
            releaseId = release.id,
            status = BlockStatus.RUNNING,
            startedAt = startTime,
        ))

        executeWithBlockErrorHandling(release.id, block.id, startTime, statusMap, outputsMap) {
            executeAndHandlePostGate(release, block, startTime, statusMap, outputsMap, run)
        }
    }

    private suspend fun awaitGateApproval(releaseId: ReleaseId, blockId: BlockId) {
        val deferred = CompletableDeferred<Map<String, String>>()
        pendingApprovals.getOrPut(releaseId) { ConcurrentHashMap() }[blockId] = deferred
        try {
            deferred.await()
        } finally {
            pendingApprovals[releaseId]?.remove(blockId)
        }
    }

    private fun registerDeferred(releaseId: ReleaseId, blockId: BlockId): CompletableDeferred<Map<String, String>> {
        val deferred = CompletableDeferred<Map<String, String>>()
        pendingApprovals.getOrPut(releaseId) { ConcurrentHashMap() }[blockId] = deferred
        return deferred
    }

    private fun resolveGateMessage(
        gate: Gate,
        blockName: String,
        phase: GatePhase,
        release: Release,
        outputsMap: Map<BlockId, Map<String, String>>,
    ): String {
        val template = gate.message.ifEmpty {
            when (phase) {
                GatePhase.PRE -> "Approve to start '$blockName'"
                GatePhase.POST -> "'$blockName' completed. Review output and approve to continue."
            }
        }
        return TemplateEngine.resolve(template, release.parameters, outputsMap)
    }

    // ── Shared helpers ──────────────────────────────────────────────────

    private fun initStatusFromPersisted(
        blocks: List<Block>,
        execMap: Map<BlockId, BlockExecution>,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        for (block in blocks) {
            val exec = execMap[block.id]
            statusMap[block.id] = exec?.status ?: BlockStatus.WAITING
            if (exec != null && exec.status == BlockStatus.SUCCEEDED) {
                outputsMap[block.id] = exec.outputs
            }
        }
    }

    /**
     * Release-level error handling: marks active blocks as FAILED on cancellation,
     * sets the release to CANCELLED or FAILED.
     */
    private suspend fun executeWithReleaseErrorHandling(
        release: Release,
        action: suspend () -> Unit,
    ) {
        try {
            action()
        } catch (_: CancellationException) {
            if (restartingReleases.contains(release.id) || stoppingReleases.contains(release.id)) return
            // Must use NonCancellable to ensure cleanup suspend calls complete in a cancelled scope
            withContext(NonCancellable) {
                val executions = repository.findBlockExecutions(release.id)
                for (exec in executions) {
                    if (exec.status == BlockStatus.RUNNING || exec.status == BlockStatus.WAITING || exec.status == BlockStatus.WAITING_FOR_INPUT) {
                        persistAndEmit(release.id, exec.copy(
                            status = BlockStatus.FAILED,
                            error = "Release cancelled",
                            finishedAt = Clock.System.now(),
                        ))
                    }
                }
                repository.setFinished(release.id, ReleaseStatus.CANCELLED)
                emitCompletionOnce(release.id, ReleaseStatus.CANCELLED, Clock.System.now())
            }
        } catch (_: Exception) {
            repository.setFinished(release.id, ReleaseStatus.FAILED)
            emitCompletionOnce(release.id, ReleaseStatus.FAILED, Clock.System.now())
        }
    }

    /**
     * Event-driven block execution loop: launches each block as soon as its predecessors succeed.
     * EXEC-C1: Replaces the previous wave-based approach that serialized unrelated blocks sharing
     * a topological level and busy-polled with delay(100ms). Now uses a Channel to signal
     * block completion, eliminating both unnecessary serialization and busy-polling.
     */
    private suspend fun runWaveLoop(
        scope: CoroutineScope,
        release: Release,
        graph: DagGraph,
        remaining: MutableList<BlockId>,
        predecessors: Map<BlockId, Set<BlockId>>,
        statusMap: MutableMap<BlockId, BlockStatus>,
        executeBlock: suspend (Block) -> Unit,
    ) {
        // Channel signaled whenever any block reaches a terminal state
        val blockCompleted = Channel<Unit>(Channel.UNLIMITED)
        val inFlightCount = AtomicInteger(0)

        fun launchReadyBlocks(): Int {
            val ready = remaining.filter { blockId ->
                val preds = predecessors[blockId] ?: emptySet()
                preds.all { statusMap[it] == BlockStatus.SUCCEEDED }
            }
            for (blockId in ready) {
                remaining.remove(blockId)
                inFlightCount.incrementAndGet()
                val block = graph.blocks.find { it.id == blockId }
                    ?: error("Block $blockId not found in DAG despite being in topological sort")
                scope.launch {
                    try {
                        executeBlock(block)
                    } finally {
                        inFlightCount.decrementAndGet()
                        blockCompleted.trySend(Unit)
                    }
                }
            }
            return ready.size
        }

        launchReadyBlocks()

        while (remaining.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (inFlightCount.get() == 0) {
                // All in-flight blocks finished — check once more for newly ready blocks
                // (handles the race where multiple blocks complete before the loop iterates)
                if (launchReadyBlocks() == 0) break // Truly stuck: remaining blocks have failed predecessors
                continue
            }
            blockCompleted.receive() // Suspend until a block completes
            launchReadyBlocks()
        }

        // Wait for all in-flight blocks to finish
        while (inFlightCount.get() > 0) {
            blockCompleted.receive()
        }

        blockCompleted.close()

        // Mark unreachable blocks (whose predecessors failed) as FAILED
        for (blockId in remaining) {
            statusMap[blockId] = BlockStatus.FAILED
            persistAndEmit(release.id, BlockExecution(
                blockId = blockId,
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = "Skipped: predecessor failed",
            ))
        }
    }

    /**
     * Resolve template parameters, load connections, and call the executor.
     */
    private suspend fun resolveAndExecute(
        release: Release,
        block: Block.ActionBlock,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        run: suspend BlockExecutor.(Block.ActionBlock, List<Parameter>, ExecutionContext, ExecutionScope?) -> Map<String, String>,
    ): Map<String, String> {
        val resolvedParams = TemplateEngine.resolveParameters(
            block.parameters,
            release.parameters,
            outputsMap,
        )

        val connections = mutableMapOf<ConnectionId, ConnectionConfig>()
        block.connectionId?.let { connId ->
            val connection = connectionsRepository.findById(connId)
                ?: throw IllegalStateException("Connection ${connId.value} not found for block ${block.id.value}")
            connections[connId] = connection.config
        }

        val context = ExecutionContext(
            releaseId = release.id,
            parameters = release.parameters,
            blockOutputs = outputsMap,
            connections = connections,
        )

        // Create ExecutionScope for executor callbacks
        val executionScope = object : ExecutionScope {
            override suspend fun persistOutputs(outputs: Map<String, String>) {
                val existing = outputsMap[block.id] ?: emptyMap()
                val merged = existing + outputs
                outputsMap[block.id] = merged
                // Upsert with merged outputs, preserving the original startedAt
                val existingExec = repository.findBlockExecution(release.id, block.id)
                repository.upsertBlockExecution(
                    BlockExecution(
                        blockId = block.id,
                        releaseId = release.id,
                        status = BlockStatus.RUNNING,
                        outputs = merged,
                        startedAt = existingExec?.startedAt ?: Clock.System.now(),
                    )
                )
            }

            override suspend fun updateSubBuilds(subBuilds: List<SubBuild>) {
                repository.updateSubBuilds(release.id, block.id, subBuilds)
                val execution = repository.findBlockExecution(release.id, block.id)
                if (execution != null) {
                    emitBlockUpdate(release.id, execution)
                }
            }
        }

        val timeoutSec = block.timeoutSeconds
        return if (timeoutSec != null) {
            withTimeoutOrNull(timeoutSec.seconds) {
                blockExecutor.run(block, resolvedParams, context, executionScope)
            } ?: throw RuntimeException("Block '${block.name}' timed out after ${timeoutSec}s")
        } else {
            blockExecutor.run(block, resolvedParams, context, executionScope)
        }
    }

    private suspend fun completeBlockSuccess(
        releaseId: ReleaseId,
        blockId: BlockId,
        outputs: Map<String, String>,
        startTime: Instant,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        outputsMap[blockId] = outputs
        statusMap[blockId] = BlockStatus.SUCCEEDED
        // Preserve approvals from the WAITING_FOR_INPUT state when transitioning to SUCCEEDED
        val existingExecution = repository.findBlockExecution(releaseId, blockId)
        val approvals = existingExecution?.approvals ?: emptyList()
        persistAndEmit(releaseId, BlockExecution(
            blockId = blockId,
            releaseId = releaseId,
            status = BlockStatus.SUCCEEDED,
            outputs = outputs,
            approvals = approvals,
            startedAt = startTime,
            finishedAt = Clock.System.now(),
        ))
    }

    /**
     * Block-level error handling: records cancellation or failure for block execution errors.
     * EXEC-C3: Success persistence is separated from the execution try-catch so that
     * a DB error in [completeBlockSuccess] does NOT mark a successfully executed block as FAILED.
     */
    private suspend fun executeWithBlockErrorHandling(
        releaseId: ReleaseId,
        blockId: BlockId,
        startTime: Instant,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        action: suspend () -> Map<String, String>,
    ) {
        val outputs: Map<String, String>
        try {
            outputs = action()
        } catch (e: CancellationException) {
            if (!restartingReleases.contains(releaseId) && !stoppingReleases.contains(releaseId)) {
                statusMap[blockId] = BlockStatus.FAILED
                persistAndEmit(releaseId, BlockExecution(
                    blockId = blockId,
                    releaseId = releaseId,
                    status = BlockStatus.FAILED,
                    error = "Cancelled",
                    startedAt = startTime,
                    finishedAt = Clock.System.now(),
                ))
            }
            throw e
        } catch (e: Exception) {
            // Block execution failed — record the failure
            statusMap[blockId] = BlockStatus.FAILED
            persistAndEmit(releaseId, BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.FAILED,
                error = e.message ?: "Unknown error",
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            ))
            return
        }

        // Block execution succeeded — persist success separately.
        // DB errors here propagate up (handled at release level) rather than falsely marking the block as FAILED.
        completeBlockSuccess(releaseId, blockId, outputs, startTime, statusMap, outputsMap)
    }

    private suspend fun persistAndEmit(releaseId: ReleaseId, execution: BlockExecution) {
        repository.upsertBlockExecution(execution)
        // Filter internal outputs (keys starting with '_') from WebSocket broadcasts
        val filteredExecution = if (execution.outputs.any { it.key.startsWith("_") }) {
            execution.copy(outputs = execution.outputs.filterKeys { !it.startsWith("_") })
        } else {
            execution
        }
        emitEvent(ReleaseEvent.BlockExecutionUpdated(releaseId, filteredExecution))
    }

    private fun registerJob(releaseId: ReleaseId, job: Job) {
        activeJobs[releaseId] = job
        job.invokeOnCompletion {
            if (!restartingReleases.contains(releaseId) && !stoppingReleases.contains(releaseId)) {
                activeJobs.remove(releaseId)
                pendingApprovals.remove(releaseId)
                completionEmitted.remove(releaseId)
                replayBuffers.remove(releaseId)
                sequenceCounters.remove(releaseId)
                restartMutexes.remove(releaseId)
            }
        }
    }

    private fun buildPredecessorMap(graph: DagGraph): Map<BlockId, Set<BlockId>> {
        val result = mutableMapOf<BlockId, MutableSet<BlockId>>()
        for (block in graph.blocks) {
            result[block.id] = mutableSetOf()
        }
        for (edge in graph.edges) {
            result.getOrPut(edge.toBlockId) { mutableSetOf() }.add(edge.fromBlockId)
        }
        return result
    }
}
