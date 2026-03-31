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
import kotlinx.coroutines.sync.Semaphore
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

    // Blocks currently being individually stopped (prevents executeWithBlockErrorHandling from marking FAILED)
    private val stoppingBlocks: MutableSet<Pair<ReleaseId, BlockId>> = ConcurrentHashMap.newKeySet()

    // Per-release mutex to prevent concurrent restarts
    private val restartMutexes = ConcurrentHashMap<ReleaseId, Mutex>()

    // Maps each block to the WaveLoopState managing it (for per-block stop/resume)
    private val blockWaveLoopStates = ConcurrentHashMap<Pair<ReleaseId, BlockId>, WaveLoopState>()

    // Per-block jobs for targeted cancellation
    private val activeBlockJobs = ConcurrentHashMap<Pair<ReleaseId, BlockId>, Job>()

    // EXEC-H7: Global concurrency semaphore for parallel block execution.
    // Limits the total number of concurrently executing blocks across all releases
    // to prevent unbounded coroutine/resource consumption.
    private val blockSemaphore = Semaphore(MAX_CONCURRENT_BLOCKS)

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

            // Skip already SUCCEEDED and STOPPED blocks from remaining
            val remaining = sorted.filter {
                statusMap[it] != BlockStatus.SUCCEEDED && statusMap[it] != BlockStatus.STOPPED
            }.toMutableList()

            coroutineScope {
                runWaveLoop(this, release, graph, remaining, predecessors, statusMap, outputsMap) { block ->
                    recoverBlock(release, block, statusMap, outputsMap, execMap, persistedExecutions)
                }
            }

            // If release was auto-stopped (still has STOPPED blocks), don't finalize
            val currentRelease = repository.findById(release.id)
            if (currentRelease?.status == ReleaseStatus.STOPPED) return@executeWithReleaseErrorHandling

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
        val childExecMap = persistedExecutions.associateBy { it.blockId }
        val persistedExec = childExecMap[container.id]

        // Handle WAITING_FOR_INPUT recovery for container gates
        if (persistedExec?.status == BlockStatus.WAITING_FOR_INPUT) {
            statusMap[container.id] = BlockStatus.WAITING_FOR_INPUT
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, persistedExec))

            val gatePhase = persistedExec.gatePhase
            if (gatePhase == null) {
                log.warn("Container ${container.id.value} has WAITING_FOR_INPUT but no gatePhase — marking FAILED")
                statusMap[container.id] = BlockStatus.FAILED
                persistAndEmit(release.id, BlockExecution(
                    blockId = container.id,
                    releaseId = release.id,
                    status = BlockStatus.FAILED,
                    error = "Recovery failed: missing gate phase",
                ))
                return
            }
            val gate = when (gatePhase) {
                GatePhase.PRE -> container.preGate
                GatePhase.POST -> container.postGate
            }

            val deferred = registerDeferred(release.id, container.id)
            val rule = gate?.approvalRule
            if (rule != null && rule.requiredCount > 0 && persistedExec.approvals.size >= rule.requiredCount) {
                deferred.complete(emptyMap())
            }
            try {
                deferred.await()
            } finally {
                pendingApprovals[release.id]?.remove(container.id)
            }

            if (gatePhase == GatePhase.PRE) {
                // Pre-gate resolved — clear gate state and proceed with container execution
                persistAndEmit(release.id, BlockExecution(
                    blockId = container.id,
                    releaseId = release.id,
                    status = BlockStatus.RUNNING,
                    startedAt = persistedExec.startedAt,
                ))
            } else {
                // Post-gate resolved — mark container as SUCCEEDED
                statusMap[container.id] = BlockStatus.SUCCEEDED
                persistAndEmit(release.id, BlockExecution(
                    blockId = container.id,
                    releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    startedAt = persistedExec.startedAt,
                    finishedAt = Clock.System.now(),
                ))
                return
            }
        }

        // Normal recovery / post-pre-gate recovery
        runContainer(
            release = release,
            container = container,
            statusMap = statusMap,
            outputsMap = outputsMap,
            initChildren = { childGraph, childStatusMap, childOutputsMap ->
                initStatusFromPersisted(childGraph.blocks, childExecMap, childStatusMap, childOutputsMap)
            },
            runWaves = { wavesScope, childGraph, sorted, childPredecessors, childStatusMap, childOutputsMap ->
                val remaining = sorted.filter {
                    childStatusMap[it] != BlockStatus.SUCCEEDED && childStatusMap[it] != BlockStatus.STOPPED
                }.toMutableList()
                runWaveLoop(wavesScope, release, childGraph, remaining, childPredecessors, childStatusMap, childOutputsMap) { block ->
                    recoverBlock(release, block, childStatusMap, childOutputsMap, childExecMap, persistedExecutions)
                }
            },
        )

        // Post-gate after container completes
        val postGate = container.postGate
        if (postGate != null && statusMap[container.id] == BlockStatus.SUCCEEDED) {
            val startTime = persistedExec?.startedAt ?: Clock.System.now()
            val msg = resolveGateMessage(postGate, container.name, GatePhase.POST, release, outputsMap)
            statusMap[container.id] = BlockStatus.WAITING_FOR_INPUT
            persistAndEmit(release.id, BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = BlockStatus.WAITING_FOR_INPUT,
                startedAt = startTime,
                gatePhase = GatePhase.POST,
                gateMessage = msg,
            ))
            awaitGateApproval(release.id, container.id)
            statusMap[container.id] = BlockStatus.SUCCEEDED
            persistAndEmit(release.id, BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = BlockStatus.SUCCEEDED,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            ))
        }
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
                // EXEC-H4: Pass persisted startedAt to preserve duration metrics
                resumeAction(release, block, statusMap, outputsMap, persistedExec.startedAt)
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
        persistedStartedAt: Instant? = null,
    ) {
        // EXEC-H4: Use persisted startedAt to preserve duration metrics after recovery
        val startTime = persistedStartedAt ?: Clock.System.now()
        runBlockWithPostGate(release, block, startTime, statusMap, outputsMap, BlockExecutor::resume)
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
            blockWaveLoopStates.keys.removeAll { it.first == releaseId }
            activeBlockJobs.keys.removeAll { it.first == releaseId }

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
     * Stop a specific block within a running release (per-block stop).
     * Only this block is stopped — other blocks continue running. Blocks that depend
     * on this block will not launch (their predecessors check will fail).
     * If all runnable blocks finish while stopped blocks remain, the release auto-transitions to STOPPED.
     */
    suspend fun stopBlock(releaseId: ReleaseId, blockId: BlockId): Boolean {
        val mutex = restartMutexes.getOrPut(releaseId) { Mutex() }
        return mutex.withLock {
            val key = releaseId to blockId
            val state = blockWaveLoopStates[key] ?: return@withLock false

            // Validate in-memory status (not just DB) to prevent race
            val currentStatus = state.statusMap[blockId]
            if (currentStatus != BlockStatus.RUNNING && currentStatus != BlockStatus.WAITING_FOR_INPUT) {
                return@withLock false
            }

            // Mark intent before cancel so error handlers skip FAILED marking
            stoppingBlocks.add(key)
            // Set status in memory BEFORE cancel so wave loop sees STOPPED immediately
            state.statusMap[blockId] = BlockStatus.STOPPED

            try {
                val blockJob = activeBlockJobs[key]
                if (blockJob != null && blockJob.isActive) {
                    blockJob.cancel()
                    blockJob.join()
                }
            } finally {
                stoppingBlocks.remove(key)
            }

            // Cancel external build (best-effort)
            val release = repository.findById(releaseId)
            if (release != null) {
                cancelExternalBuild(release, blockId)
            }

            // Persist — targeted UPDATE preserves outputs, gatePhase, approvals, etc.
            repository.stopSingleBlock(releaseId, blockId, Clock.System.now())

            // Clean up pending approval for this block only
            pendingApprovals[releaseId]?.remove(blockId)

            // Emit block update only (NOT ReleaseStatusChanged — release stays RUNNING)
            val updatedExec = repository.findBlockExecution(releaseId, blockId)
            if (updatedExec != null) {
                emitBlockUpdate(releaseId, updatedExec)
            }

            // Track stopped count and wake wave loop
            state.stoppedCount.incrementAndGet()
            state.blockCompleted.trySend(Unit)

            activeBlockJobs.remove(key)

            log.info("Block {} stopped in release {}", blockId.value, releaseId.value)
            true
        }
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
            // REL-M1: Validate status inside engine (protected by mutex) to prevent TOCTOU
            if (release.status != ReleaseStatus.RUNNING) return@withLock false

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

            // Find all RUNNING/WAITING_FOR_INPUT/WAITING blocks to stop
            val executions = repository.findBlockExecutions(releaseId)
            // REL-M2: Include WAITING blocks in batch stop to prevent double-resume on resume
            val blocksToStop = executions.filter {
                it.status == BlockStatus.RUNNING || it.status == BlockStatus.WAITING_FOR_INPUT || it.status == BlockStatus.WAITING
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

            // EXEC-H3: Emit WebSocket events BEFORE clearing replay buffers
            // so reconnecting subscribers see the stop events in the replay buffer.
            val updatedExecutions = repository.findBlockExecutions(releaseId)
            for (exec in updatedExecutions) {
                if (exec.status == BlockStatus.STOPPED && blocksToStop.any { it.blockId == exec.blockId }) {
                    emitBlockUpdate(releaseId, exec)
                }
            }
            emitEvent(ReleaseEvent.ReleaseStatusChanged(releaseId, ReleaseStatus.STOPPED))

            // Clean up stale in-memory state AFTER emitting events
            activeJobs.remove(releaseId)
            pendingApprovals.remove(releaseId)
            completionEmitted.remove(releaseId)
            replayBuffers.remove(releaseId)
            sequenceCounters.remove(releaseId)
            // Clean up per-block state for this release
            blockWaveLoopStates.keys.removeAll { it.first == releaseId }
            activeBlockJobs.keys.removeAll { it.first == releaseId }

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
     * Resume a single stopped block within a running release.
     * If the block was stopped at a post-approval gate, it is restored to WAITING_FOR_INPUT
     * with its outputs preserved (the block does NOT re-execute). Otherwise, it resets to WAITING.
     */
    suspend fun resumeBlock(releaseId: ReleaseId, blockId: BlockId): Boolean {
        val mutex = restartMutexes.getOrPut(releaseId) { Mutex() }
        return mutex.withLock {
            val key = releaseId to blockId
            val state = blockWaveLoopStates[key] ?: return@withLock false

            // If the wave loop has auto-stopped, per-block resume is not possible.
            // The user should use release-level resume instead.
            if (state.autoStopped.get()) return@withLock false

            val exec = repository.findBlockExecution(releaseId, blockId) ?: return@withLock false
            if (exec.status != BlockStatus.STOPPED) return@withLock false

            // Decrement stoppedCount and modify remaining atomically (synchronized with wave loop)
            synchronized(state.remaining) {
                state.stoppedCount.decrementAndGet()

                if (exec.gatePhase != GatePhase.POST) {
                    // Normal or pre-gate resume: add back to remaining
                    state.remaining.add(blockId)
                }
            }

            if (exec.gatePhase == GatePhase.POST) {
                // Post-gate resume: restore to WAITING_FOR_INPUT, preserve outputs
                repository.resumeSingleBlockToGate(releaseId, blockId)
                state.statusMap[blockId] = BlockStatus.WAITING_FOR_INPUT

                val updatedExec = repository.findBlockExecution(releaseId, blockId)
                if (updatedExec != null) {
                    emitBlockUpdate(releaseId, updatedExec)
                }

                // Launch a coroutine in the wave loop's scope to wait for post-gate approval
                state.inFlightCount.incrementAndGet()
                val job = state.scope.launch {
                    try {
                        awaitGateApproval(releaseId, blockId)
                        // Complete with stored outputs — NO re-execution
                        val startTime = exec.startedAt ?: Clock.System.now()
                        completeBlockSuccess(releaseId, blockId, exec.outputs, startTime, state.statusMap, state.outputsMap)
                    } catch (e: CancellationException) {
                        val blockKey = releaseId to blockId
                        if (!stoppingBlocks.contains(blockKey) && !stoppingReleases.contains(releaseId) && !restartingReleases.contains(releaseId)) {
                            state.statusMap[blockId] = BlockStatus.FAILED
                            persistAndEmit(releaseId, BlockExecution(
                                blockId = blockId,
                                releaseId = releaseId,
                                status = BlockStatus.FAILED,
                                error = "Cancelled",
                                startedAt = exec.startedAt,
                                finishedAt = Clock.System.now(),
                            ))
                        }
                        throw e
                    } catch (e: Exception) {
                        state.statusMap[blockId] = BlockStatus.FAILED
                        persistAndEmit(releaseId, BlockExecution(
                            blockId = blockId,
                            releaseId = releaseId,
                            status = BlockStatus.FAILED,
                            error = e.message ?: "Unknown error",
                            startedAt = exec.startedAt,
                            finishedAt = Clock.System.now(),
                        ))
                    } finally {
                        activeBlockJobs.remove(key)
                        state.inFlightCount.decrementAndGet()
                        state.blockCompleted.trySend(Unit)
                    }
                }
                activeBlockJobs[key] = job
            } else {
                // Normal or pre-gate resume: reset to WAITING, re-execute from scratch
                repository.resumeSingleBlockToWaiting(releaseId, blockId)
                state.statusMap[blockId] = BlockStatus.WAITING

                val updatedExec = repository.findBlockExecution(releaseId, blockId)
                if (updatedExec != null) {
                    emitBlockUpdate(releaseId, updatedExec)
                }
            }

            state.blockCompleted.trySend(Unit)

            log.info("Block {} resumed in release {} (gatePhase={})", blockId.value, releaseId.value, exec.gatePhase)
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
                runWaveLoop(this, release, graph, remaining, predecessors, statusMap, outputsMap) { block ->
                    executeBlock(release, block, statusMap, outputsMap)
                }
            }

            // If release was auto-stopped (still has STOPPED blocks), don't finalize
            val currentRelease = repository.findById(release.id)
            if (currentRelease?.status == ReleaseStatus.STOPPED) return@executeWithReleaseErrorHandling

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
        overrideStartTime: Instant? = null,
    ) {
        val startTime = overrideStartTime ?: Clock.System.now()
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
            // If the container itself was individually stopped, set STOPPED and return
            if (stoppingBlocks.contains(release.id to container.id)) {
                statusMap[container.id] = BlockStatus.STOPPED
                return
            }
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
        val startTime = Clock.System.now()

        // Pre-gate (if configured)
        val preGate = container.preGate
        if (preGate != null) {
            val msg = resolveGateMessage(preGate, container.name, GatePhase.PRE, release, outputsMap)
            statusMap[container.id] = BlockStatus.WAITING_FOR_INPUT
            persistAndEmit(release.id, BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = BlockStatus.WAITING_FOR_INPUT,
                startedAt = startTime,
                gatePhase = GatePhase.PRE,
                gateMessage = msg,
            ))
            awaitGateApproval(release.id, container.id)
        }

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
                runWaveLoop(wavesScope, release, childGraph, remaining, childPredecessors, childStatusMap, childOutputsMap) { block ->
                    executeBlock(release, block, childStatusMap, childOutputsMap)
                }
            },
            overrideStartTime = if (preGate != null) startTime else null,
        )

        // Post-gate (if configured)
        val postGate = container.postGate
        if (postGate != null && statusMap[container.id] == BlockStatus.SUCCEEDED) {
            val msg = resolveGateMessage(postGate, container.name, GatePhase.POST, release, outputsMap)
            statusMap[container.id] = BlockStatus.WAITING_FOR_INPUT
            persistAndEmit(release.id, BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = BlockStatus.WAITING_FOR_INPUT,
                startedAt = startTime,
                gatePhase = GatePhase.POST,
                gateMessage = msg,
            ))
            awaitGateApproval(release.id, container.id)
            statusMap[container.id] = BlockStatus.SUCCEEDED
            persistAndEmit(release.id, BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = BlockStatus.SUCCEEDED,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            ))
        }
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
            val namespacedOutputs = outputs.flatMap { (k, v) ->
                listOf("outputs.$k" to v, k to v)
            }.toMap()
            val fullMap = (outputsMap[block.id] ?: emptyMap()) + namespacedOutputs
            outputsMap[block.id] = fullMap
            val msg = resolveGateMessage(postGate, block.name, GatePhase.POST, release, outputsMap)
            statusMap[block.id] = BlockStatus.WAITING_FOR_INPUT
            persistAndEmit(release.id, BlockExecution(
                blockId = block.id,
                releaseId = release.id,
                status = BlockStatus.WAITING_FOR_INPUT,
                outputs = fullMap,
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
     * EXEC-C1: Uses a Channel to signal block completion, eliminating busy-polling.
     *
     * Supports per-block stop/resume: external [stopBlock]/[resumeBlock] calls interact with the
     * loop via [WaveLoopState]. The loop stays alive while stopped blocks exist (they may be resumed).
     * If all runnable blocks finish while stopped blocks remain, the release auto-transitions to STOPPED.
     */
    private suspend fun runWaveLoop(
        scope: CoroutineScope,
        release: Release,
        graph: DagGraph,
        remaining: MutableList<BlockId>,
        predecessors: Map<BlockId, Set<BlockId>>,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        executeBlock: suspend (Block) -> Unit,
    ) {
        val blockCompleted = Channel<Unit>(Channel.UNLIMITED)
        val inFlightCount = AtomicInteger(0)
        val blockById = graph.blocks.associateBy { it.id }

        // Pre-count stopped blocks (relevant during recovery)
        val initialStoppedCount = statusMap.values.count { it == BlockStatus.STOPPED }

        val state = WaveLoopState(
            releaseId = release.id,
            scope = scope,
            remaining = remaining,
            statusMap = statusMap,
            outputsMap = outputsMap,
            blockCompleted = blockCompleted,
            inFlightCount = inFlightCount,
            stoppedCount = AtomicInteger(initialStoppedCount),
        )

        // Register all managed blocks so stopBlock/resumeBlock can find the state
        for (block in graph.blocks) {
            blockWaveLoopStates[release.id to block.id] = state
        }

        try {
            fun launchReadyBlocks(): Int {
                return synchronized(state.remaining) {
                    val ready = state.remaining.filter { blockId ->
                        val preds = predecessors[blockId] ?: emptySet()
                        preds.all { statusMap[it] == BlockStatus.SUCCEEDED }
                    }
                    for (blockId in ready) {
                        state.remaining.remove(blockId)
                        inFlightCount.incrementAndGet()
                        val block = blockById[blockId]
                            ?: error("Block $blockId not found in DAG despite being in topological sort")
                        val key = release.id to blockId
                        val job = scope.launch {
                            try {
                                executeBlock(block)
                            } finally {
                                activeBlockJobs.remove(key)
                                inFlightCount.decrementAndGet()
                                blockCompleted.trySend(Unit)
                            }
                        }
                        activeBlockJobs[key] = job
                    }
                    ready.size
                }
            }

            launchReadyBlocks()

            while (true) {
                currentCoroutineContext().ensureActive()

                // Atomically check termination and auto-stop conditions in a single synchronized block
                // to prevent TOCTOU race with resumeBlock modifying stoppedCount + remaining.
                // 0 = continue, 1 = break, 2 = auto-stop
                val action = synchronized(state.remaining) {
                    when {
                        // All done: no remaining, no in-flight, no stopped
                        state.remaining.isEmpty() && inFlightCount.get() == 0 && state.stoppedCount.get() == 0 -> 1

                        // No in-flight, no stopped, but remaining exist — try to launch
                        inFlightCount.get() == 0 && state.stoppedCount.get() == 0 ->
                            if (launchReadyBlocks() == 0) 1 else 0

                        // All runnable work done, but stopped blocks remain — auto-stop
                        state.remaining.isEmpty() && inFlightCount.get() == 0 && state.stoppedCount.get() > 0 -> {
                            // Set flag INSIDE synchronized to prevent resumeBlock from racing
                            state.autoStopped.set(true)
                            2
                        }

                        else -> 0
                    }
                }
                if (action == 1) break
                if (action == 2) {
                    repository.updateStatus(release.id, ReleaseStatus.STOPPED)
                    emitEvent(ReleaseEvent.ReleaseStatusChanged(release.id, ReleaseStatus.STOPPED))
                    log.info("Release {} auto-stopped: all runnable blocks done, {} blocks still stopped",
                        release.id.value, state.stoppedCount.get())
                    return // Exit wave loop — release is now STOPPED
                }

                blockCompleted.receive()
                launchReadyBlocks()
            }

            // Wait for all in-flight blocks to finish
            while (inFlightCount.get() > 0) {
                blockCompleted.receive()
            }

            blockCompleted.close()

            // Mark unreachable blocks (whose predecessors failed) as FAILED
            val unreachable = synchronized(state.remaining) { state.remaining.toList() }
            for (blockId in unreachable) {
                statusMap[blockId] = BlockStatus.FAILED
                persistAndEmit(release.id, BlockExecution(
                    blockId = blockId,
                    releaseId = release.id,
                    status = BlockStatus.FAILED,
                    error = "Skipped: predecessor failed",
                ))
            }
        } finally {
            // Clean up block registrations
            for (block in graph.blocks) {
                blockWaveLoopStates.remove(release.id to block.id)
            }
        }
    }

    /**
     * Resolve template parameters, load connections, and call the executor.
     */
    /**
     * EXEC-H7: Semaphore is acquired here (around actual block execution) rather than
     * in runWaveLoop, so that blocks waiting for pre/post-gate approval don't hold a permit.
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
            blockId = block.id,
        )

        // Store resolved inputs so downstream blocks can reference them via ${block.<id>.inputs.<key>}
        val inputEntries = resolvedParams
            .filter { it.key.isNotBlank() }
            .associate { "inputs.${it.key}" to it.value }
        outputsMap[block.id] = (outputsMap[block.id] ?: emptyMap()) + inputEntries

        val connections = mutableMapOf<ConnectionId, ConnectionConfig>()
        block.connectionId?.let { connId ->
            val connection = connectionsRepository.findById(connId)
                ?: throw IllegalStateException("Connection ${connId.value} not found for block ${block.id.value}")
            connections[connId] = connection.config
        }

        // EXEC-M3: Pass an immutable snapshot so executors see a consistent view
        // and can't accidentally mutate the shared map
        val context = ExecutionContext(
            releaseId = release.id,
            parameters = release.parameters,
            blockOutputs = outputsMap.toMap(),
            connections = connections,
        )

        // Create ExecutionScope for executor callbacks
        val executionScope = object : ExecutionScope {
            override suspend fun persistOutputs(outputs: Map<String, String>) {
                val existing = outputsMap[block.id] ?: emptyMap()
                val namespacedOutputs = outputs.flatMap { (k, v) ->
                    listOf("outputs.$k" to v, k to v)
                }.toMap()
                val merged = existing + namespacedOutputs
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
        // EXEC-H7: Acquire semaphore only around actual execution, not gate waits
        blockSemaphore.acquire()
        try {
            return if (timeoutSec != null) {
                withTimeoutOrNull(timeoutSec.seconds) {
                    blockExecutor.run(block, resolvedParams, context, executionScope)
                } ?: throw RuntimeException("Block '${block.name}' timed out after ${timeoutSec}s")
            } else {
                blockExecutor.run(block, resolvedParams, context, executionScope)
            }
        } finally {
            blockSemaphore.release()
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
        // Store outputs with namespaced keys + plain keys for backward compat
        val namespacedOutputs = outputs.flatMap { (k, v) ->
            listOf("outputs.$k" to v, k to v)
        }.toMap()
        val fullMap = (outputsMap[blockId] ?: emptyMap()) + namespacedOutputs
        outputsMap[blockId] = fullMap
        statusMap[blockId] = BlockStatus.SUCCEEDED
        // Preserve approvals from the WAITING_FOR_INPUT state when transitioning to SUCCEEDED
        val existingExecution = repository.findBlockExecution(releaseId, blockId)
        val approvals = existingExecution?.approvals ?: emptyList()
        persistAndEmit(releaseId, BlockExecution(
            blockId = blockId,
            releaseId = releaseId,
            status = BlockStatus.SUCCEEDED,
            outputs = fullMap,
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
            val blockKey = releaseId to blockId
            if (!restartingReleases.contains(releaseId)
                && !stoppingReleases.contains(releaseId)
                && !stoppingBlocks.contains(blockKey)) {
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
                blockWaveLoopStates.keys.removeAll { it.first == releaseId }
                activeBlockJobs.keys.removeAll { it.first == releaseId }
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

    /**
     * Tracks the mutable state of an active wave loop so that external
     * [stopBlock]/[resumeBlock] calls can interact with it.
     *
     * ALL access to [remaining] must be wrapped in `synchronized(remaining)`.
     */
    class WaveLoopState(
        val releaseId: ReleaseId,
        val scope: CoroutineScope,
        val remaining: MutableList<BlockId>,
        val statusMap: MutableMap<BlockId, BlockStatus>,
        val outputsMap: MutableMap<BlockId, Map<String, String>>,
        val blockCompleted: Channel<Unit>,
        val inFlightCount: AtomicInteger,
        val stoppedCount: AtomicInteger = AtomicInteger(0),
        /** Set to true by the wave loop when it decides to auto-stop. Checked by resumeBlock to prevent a race. */
        val autoStopped: AtomicBoolean = AtomicBoolean(false),
    )

    companion object {
        /** EXEC-H7: Maximum concurrent block executions across all releases */
        const val MAX_CONCURRENT_BLOCKS = 50
    }
}
