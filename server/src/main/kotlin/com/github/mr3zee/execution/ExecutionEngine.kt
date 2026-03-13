package com.github.mr3zee.execution

import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.connections.ConnectionsRepository
import com.github.mr3zee.dag.DagTopologicalSort
import com.github.mr3zee.dag.DagValidator
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleasesRepository
import com.github.mr3zee.template.TemplateEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

/**
 * Coroutine-based DAG execution engine.
 *
 * For each release, traverses the DAG in topological order, launching blocks
 * whose predecessors have all SUCCEEDED. Container blocks recursively execute
 * their sub-DAG. User Action blocks suspend on CompletableDeferred until approved.
 */
class ExecutionEngine(
    private val repository: ReleasesRepository,
    private val blockExecutor: BlockExecutor,
    private val connectionsRepository: ConnectionsRepository,
) {
    private val log = LoggerFactory.getLogger(ExecutionEngine::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Active release jobs, keyed by release ID
    private val activeJobs = ConcurrentHashMap<ReleaseId, Job>()

    // Tracks whether completion events have been emitted for a release (prevents duplicates)
    private val completionEmitted = ConcurrentHashMap<ReleaseId, java.util.concurrent.atomic.AtomicBoolean>()

    // Releases currently being restarted (cancel-and-recover in progress)
    private val restartingReleases = ConcurrentHashMap.newKeySet<ReleaseId>()

    // Per-release mutex to prevent concurrent restarts
    private val restartMutexes = ConcurrentHashMap<ReleaseId, kotlinx.coroutines.sync.Mutex>()

    // Pending user action approvals: releaseId -> blockId -> CompletableDeferred
    private val pendingApprovals = ConcurrentHashMap<ReleaseId, ConcurrentHashMap<BlockId, CompletableDeferred<Map<String, String>>>>()

    // Event stream for WebSocket subscribers
    private val _events = MutableSharedFlow<ReleaseEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ReleaseEvent> = _events.asSharedFlow()

    private fun emitEvent(event: ReleaseEvent) {
        _events.tryEmit(event)
    }

    /**
     * Emit release completion events exactly once per release.
     * Both [cancelExecution] and [executeRelease]'s catch block can trigger completion;
     * the AtomicBoolean ensures only the first caller emits events.
     */
    private fun emitCompletionOnce(releaseId: ReleaseId, status: ReleaseStatus, finishedAt: kotlin.time.Instant) {
        val flag = completionEmitted.getOrPut(releaseId) { java.util.concurrent.atomic.AtomicBoolean(false) }
        if (flag.compareAndSet(false, true)) {
            emitEvent(ReleaseEvent.ReleaseStatusChanged(releaseId, status, finishedAt = finishedAt))
            emitEvent(ReleaseEvent.ReleaseCompleted(releaseId, status, finishedAt = finishedAt))
        }
    }

    fun startExecution(release: Release): Job {
        log.info("Starting execution for release {}", release.id.value)
        completionEmitted[release.id] = java.util.concurrent.atomic.AtomicBoolean(false)
        val job = scope.launch {
            executeRelease(release)
        }
        activeJobs[release.id] = job
        job.invokeOnCompletion {
            if (!restartingReleases.contains(release.id)) {
                activeJobs.remove(release.id)
                pendingApprovals.remove(release.id)
                completionEmitted.remove(release.id)
            }
        }
        return job
    }

    suspend fun awaitExecution(releaseId: ReleaseId) {
        activeJobs[releaseId]?.join()
    }

    suspend fun cancelExecution(releaseId: ReleaseId) {
        val job = activeJobs[releaseId] ?: return
        pendingApprovals[releaseId]?.values?.forEach {
            it.completeExceptionally(CancellationException("Release cancelled"))
        }
        job.cancel()
        job.join()
        // Ensure CANCELLED is written even if the job was cancelled before executeRelease ran
        val release = repository.findById(releaseId)
        if (release != null && release.status != ReleaseStatus.CANCELLED) {
            repository.setFinished(releaseId, ReleaseStatus.CANCELLED)
            emitCompletionOnce(releaseId, ReleaseStatus.CANCELLED, Clock.System.now())
        }
    }

    suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, input: Map<String, String>): Boolean {
        val approvalMap = pendingApprovals[releaseId] ?: return false
        val deferred = approvalMap[blockId] ?: return false
        deferred.complete(input)
        return true
    }

    /**
     * Resume a previously RUNNING release after server restart.
     * Reconstructs the execution state from DB records and resumes blocks
     * based on their persisted status.
     */
    fun recoverRelease(release: Release, persistedExecutions: List<BlockExecution>) {
        completionEmitted[release.id] = java.util.concurrent.atomic.AtomicBoolean(false)
        val job = scope.launch {
            executeRecovery(release, persistedExecutions)
        }
        activeJobs[release.id] = job
        job.invokeOnCompletion {
            if (!restartingReleases.contains(release.id)) {
                activeJobs.remove(release.id)
                pendingApprovals.remove(release.id)
                completionEmitted.remove(release.id)
            }
        }
    }

    private suspend fun executeRecovery(release: Release, persistedExecutions: List<BlockExecution>) {
        try {
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
            for (block in graph.blocks) {
                val exec = execMap[block.id]
                statusMap[block.id] = exec?.status ?: BlockStatus.WAITING
                if (exec != null && exec.status == BlockStatus.SUCCEEDED) {
                    outputsMap[block.id] = exec.outputs
                }
            }

            val startedAt = release.startedAt ?: Clock.System.now()

            // Re-emit current state so WebSocket subscribers see the recovered state
            emitEvent(ReleaseEvent.ReleaseStatusChanged(release.id, ReleaseStatus.RUNNING, startedAt = startedAt))

            coroutineScope {
                executeRecoveryWaves(this, release, graph, sorted, predecessors, statusMap, outputsMap, execMap)
            }

            val allSucceeded = statusMap.values.all { it == BlockStatus.SUCCEEDED }
            val finalStatus = if (allSucceeded) ReleaseStatus.SUCCEEDED else ReleaseStatus.FAILED
            repository.setFinished(release.id, finalStatus)
            emitCompletionOnce(release.id, finalStatus, Clock.System.now())

        } catch (e: CancellationException) {
            if (restartingReleases.contains(release.id)) return
            val executions = repository.findBlockExecutions(release.id)
            for (exec in executions) {
                if (exec.status == BlockStatus.RUNNING || exec.status == BlockStatus.WAITING) {
                    val updated = exec.copy(
                        status = BlockStatus.FAILED,
                        error = "Release cancelled",
                        finishedAt = Clock.System.now(),
                    )
                    repository.upsertBlockExecution(updated)
                    emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, updated))
                }
            }
            repository.setFinished(release.id, ReleaseStatus.CANCELLED)
            emitCompletionOnce(release.id, ReleaseStatus.CANCELLED, Clock.System.now())
        } catch (e: Exception) {
            repository.setFinished(release.id, ReleaseStatus.FAILED)
            emitCompletionOnce(release.id, ReleaseStatus.FAILED, Clock.System.now())
        }
    }

    private suspend fun executeRecoveryWaves(
        scope: CoroutineScope,
        release: Release,
        graph: DagGraph,
        sorted: List<BlockId>,
        predecessors: Map<BlockId, Set<BlockId>>,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        execMap: Map<BlockId, BlockExecution>,
    ) {
        // Skip already SUCCEEDED blocks
        val remaining = sorted.filter { statusMap[it] != BlockStatus.SUCCEEDED }.toMutableList()

        while (remaining.isNotEmpty()) {
            coroutineContext.ensureActive()

            val ready = remaining.filter { blockId ->
                val preds = predecessors[blockId] ?: emptySet()
                preds.all { statusMap[it] == BlockStatus.SUCCEEDED }
            }

            if (ready.isEmpty()) {
                val anyRunning = statusMap.values.any {
                    it == BlockStatus.RUNNING || it == BlockStatus.WAITING_FOR_INPUT
                }
                if (anyRunning) {
                    delay(50)
                    continue
                }
                break
            }

            ready.forEach { remaining.remove(it) }

            val jobs = ready.map { blockId ->
                val block = graph.blocks.find { it.id == blockId }!!
                val persistedExec = execMap[blockId]
                scope.async {
                    recoverBlock(release, block, statusMap, outputsMap, persistedExec)
                }
            }

            jobs.forEach { it.await() }
        }

        // Mark any remaining blocks as FAILED
        for (blockId in remaining) {
            statusMap[blockId] = BlockStatus.FAILED
            val execution = BlockExecution(
                blockId = blockId,
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = "Skipped: predecessor failed",
            )
            repository.upsertBlockExecution(execution)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, execution))
        }
    }

    private suspend fun recoverBlock(
        release: Release,
        block: Block,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
        persistedExec: BlockExecution?,
    ) {
        when (block) {
            is Block.ContainerBlock -> recoverContainer(release, block, statusMap, outputsMap)
            is Block.ActionBlock -> recoverAction(release, block, statusMap, outputsMap, persistedExec)
        }
    }

    private suspend fun recoverContainer(
        release: Release,
        container: Block.ContainerBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        val startTime = Clock.System.now()
        statusMap[container.id] = BlockStatus.RUNNING
        val runningExec = BlockExecution(
            blockId = container.id,
            releaseId = release.id,
            status = BlockStatus.RUNNING,
            startedAt = startTime,
        )
        repository.upsertBlockExecution(runningExec)
        emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, runningExec))

        try {
            val childGraph = container.children
            if (childGraph.blocks.isEmpty()) {
                statusMap[container.id] = BlockStatus.SUCCEEDED
                val succeededExec = BlockExecution(
                    blockId = container.id,
                    releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    startedAt = startTime,
                    finishedAt = Clock.System.now(),
                )
                repository.upsertBlockExecution(succeededExec)
                emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, succeededExec))
                return
            }

            val sorted = DagTopologicalSort.sort(childGraph)
                ?: throw IllegalStateException("Container sub-DAG contains a cycle")

            val childPredecessors = buildPredecessorMap(childGraph)
            val childStatusMap = ConcurrentHashMap<BlockId, BlockStatus>()
            val childOutputsMap = ConcurrentHashMap<BlockId, Map<String, String>>()

            // Load persisted child execution statuses instead of resetting to WAITING
            val persistedChildExecs = repository.findBlockExecutions(release.id)
            val childExecMap = persistedChildExecs.associateBy { it.blockId }

            for (child in childGraph.blocks) {
                val childExec = childExecMap[child.id]
                childStatusMap[child.id] = childExec?.status ?: BlockStatus.WAITING
                if (childExec != null && childExec.status == BlockStatus.SUCCEEDED) {
                    childOutputsMap[child.id] = childExec.outputs
                }
            }

            // Use recovery waves for children too
            coroutineScope {
                executeRecoveryWaves(this, release, childGraph, sorted, childPredecessors, childStatusMap, childOutputsMap, childExecMap)
            }

            outputsMap.putAll(childOutputsMap)
            statusMap.putAll(childStatusMap)

            val allChildrenSucceeded = childStatusMap.values.all { it == BlockStatus.SUCCEEDED }
            val containerStatus = if (allChildrenSucceeded) BlockStatus.SUCCEEDED else BlockStatus.FAILED

            statusMap[container.id] = containerStatus
            val containerExec = BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = containerStatus,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            )
            repository.upsertBlockExecution(containerExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, containerExec))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            statusMap[container.id] = BlockStatus.FAILED
            val failedExec = BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = e.message,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            )
            repository.upsertBlockExecution(failedExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, failedExec))
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
                // Already done — use stored outputs
                outputsMap[block.id] = persistedExec.outputs
                statusMap[block.id] = BlockStatus.SUCCEEDED
            }
            BlockStatus.RUNNING -> {
                // Was running when server died — call resume()
                resumeAction(release, block, statusMap, outputsMap)
            }
            BlockStatus.WAITING_FOR_INPUT -> {
                // User action — re-register CompletableDeferred and wait
                val startTime = persistedExec.startedAt ?: Clock.System.now()
                statusMap[block.id] = BlockStatus.WAITING_FOR_INPUT
                emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, persistedExec))

                val deferred = CompletableDeferred<Map<String, String>>()
                pendingApprovals.getOrPut(release.id) { ConcurrentHashMap() }[block.id] = deferred

                val outputs = deferred.await()
                pendingApprovals[release.id]?.remove(block.id)

                outputsMap[block.id] = outputs
                statusMap[block.id] = BlockStatus.SUCCEEDED
                val succeededExec = BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    outputs = outputs,
                    startedAt = startTime,
                    finishedAt = Clock.System.now(),
                )
                repository.upsertBlockExecution(succeededExec)
                emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, succeededExec))
            }
            BlockStatus.FAILED -> {
                // Already failed — keep it
                statusMap[block.id] = BlockStatus.FAILED
            }
            else -> {
                // WAITING or null — execute normally
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
        val startTime = Clock.System.now()
        statusMap[block.id] = BlockStatus.RUNNING
        val runningExec = BlockExecution(
            blockId = block.id,
            releaseId = release.id,
            status = BlockStatus.RUNNING,
            startedAt = startTime,
        )
        repository.upsertBlockExecution(runningExec)
        emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, runningExec))

        try {
            val resolvedParams = TemplateEngine.resolveParameters(
                block.parameters,
                release.parameters,
                outputsMap,
            )

            val connections = mutableMapOf<ConnectionId, ConnectionConfig>()
            block.connectionId?.let { connId ->
                val connection = connectionsRepository.findById(connId)
                if (connection != null) {
                    connections[connId] = connection.config
                }
            }

            val context = ExecutionContext(
                releaseId = release.id,
                parameters = release.parameters,
                blockOutputs = outputsMap,
                connections = connections,
            )

            val timeoutMs = block.timeoutSeconds?.let { it * 1000 }
            val outputs = if (timeoutMs != null) {
                withTimeout(timeoutMs) {
                    blockExecutor.resume(block, resolvedParams, context)
                }
            } else {
                blockExecutor.resume(block, resolvedParams, context)
            }

            outputsMap[block.id] = outputs
            statusMap[block.id] = BlockStatus.SUCCEEDED
            val succeededExec = BlockExecution(
                blockId = block.id,
                releaseId = release.id,
                status = BlockStatus.SUCCEEDED,
                outputs = outputs,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            )
            repository.upsertBlockExecution(succeededExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, succeededExec))
        } catch (e: CancellationException) {
            if (!restartingReleases.contains(release.id)) {
                statusMap[block.id] = BlockStatus.FAILED
                val cancelledExec = BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.FAILED,
                    error = "Cancelled",
                    startedAt = startTime,
                    finishedAt = Clock.System.now(),
                )
                repository.upsertBlockExecution(cancelledExec)
                emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, cancelledExec))
            }
            throw e
        } catch (e: Exception) {
            statusMap[block.id] = BlockStatus.FAILED
            val failedExec = BlockExecution(
                blockId = block.id,
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = e.message ?: "Unknown error",
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            )
            repository.upsertBlockExecution(failedExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, failedExec))
        }
    }

    /**
     * Restart a failed block within a release.
     *
     * Cancels the active execution (if any), resets the block and its
     * transitive dependents to WAITING, then re-launches via recovery.
     * A per-release mutex prevents concurrent restart calls from racing.
     */
    suspend fun restartBlock(releaseId: ReleaseId, blockId: BlockId): Boolean {
        val mutex = restartMutexes.getOrPut(releaseId) { kotlinx.coroutines.sync.Mutex() }
        return mutex.withLock {
            val release = repository.findById(releaseId) ?: return@withLock false

            // Cancel current execution FIRST, before modifying DB state,
            // so the running job doesn't see partially-reset blocks
            val activeJob = activeJobs[releaseId]
            if (activeJob != null && activeJob.isActive) {
                restartingReleases.add(releaseId)
                activeJob.cancel()
                activeJob.join()
                // invokeOnCompletion has run and skipped cleanup due to restart flag
            }

            // Clear the restart flag now that the old job is fully stopped
            restartingReleases.remove(releaseId)

            // Clean up stale in-memory state from the old job
            activeJobs.remove(releaseId)
            pendingApprovals.remove(releaseId)
            completionEmitted.remove(releaseId)

            // Reset the failed block in DB
            val resetExec = BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.WAITING,
            )
            repository.upsertBlockExecution(resetExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(releaseId, resetExec))

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
                    repository.upsertBlockExecution(depReset)
                    emitEvent(ReleaseEvent.BlockExecutionUpdated(releaseId, depReset))
                }
            }

            // Ensure release is in RUNNING state
            repository.updateStatus(releaseId, ReleaseStatus.RUNNING)

            // Re-launch via recovery
            val updatedRelease = repository.findById(releaseId)!!
            val executions = repository.findBlockExecutions(releaseId)
            recoverRelease(updatedRelease, executions)

            true
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

    fun shutdown() {
        scope.cancel()
    }

    private suspend fun executeRelease(release: Release) {
        try {
            // Validate the DAG before execution
            val graph = release.dagSnapshot
            val errors = DagValidator.validate(graph)
            if (errors.isNotEmpty()) {
                repository.setFinished(release.id, ReleaseStatus.FAILED)
                emitCompletionOnce(release.id, ReleaseStatus.FAILED, Clock.System.now())
                return
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

            // Use coroutineScope for structured concurrency — cancellation propagates to all children
            coroutineScope {
                executeWaves(this, release, graph, sorted, predecessors, statusMap, outputsMap)
            }

            val allSucceeded = statusMap.values.all { it == BlockStatus.SUCCEEDED }
            val finalStatus = if (allSucceeded) ReleaseStatus.SUCCEEDED else ReleaseStatus.FAILED
            repository.setFinished(release.id, finalStatus)
            log.info("Release {} completed with status {}", release.id.value, finalStatus)
            emitCompletionOnce(release.id, finalStatus, Clock.System.now())

        } catch (e: CancellationException) {
            if (restartingReleases.contains(release.id)) return
            val executions = repository.findBlockExecutions(release.id)
            for (exec in executions) {
                if (exec.status == BlockStatus.RUNNING || exec.status == BlockStatus.WAITING) {
                    val updated = exec.copy(
                        status = BlockStatus.FAILED,
                        error = "Release cancelled",
                        finishedAt = Clock.System.now(),
                    )
                    repository.upsertBlockExecution(updated)
                    emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, updated))
                }
            }
            repository.setFinished(release.id, ReleaseStatus.CANCELLED)
            emitCompletionOnce(release.id, ReleaseStatus.CANCELLED, Clock.System.now())
        } catch (e: Exception) {
            repository.setFinished(release.id, ReleaseStatus.FAILED)
            emitCompletionOnce(release.id, ReleaseStatus.FAILED, Clock.System.now())
        }
    }

    private suspend fun executeWaves(
        scope: CoroutineScope,
        release: Release,
        graph: DagGraph,
        sorted: List<BlockId>,
        predecessors: Map<BlockId, Set<BlockId>>,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        val remaining = sorted.toMutableList()
        while (remaining.isNotEmpty()) {
            coroutineContext.ensureActive()

            val ready = remaining.filter { blockId ->
                val preds = predecessors[blockId] ?: emptySet()
                preds.all { statusMap[it] == BlockStatus.SUCCEEDED }
            }

            if (ready.isEmpty()) {
                val anyRunning = statusMap.values.any {
                    it == BlockStatus.RUNNING || it == BlockStatus.WAITING_FOR_INPUT
                }
                if (anyRunning) {
                    delay(50)
                    continue
                }
                break
            }

            // Remove ready blocks from remaining before launching
            ready.forEach { remaining.remove(it) }

            // Launch ready blocks concurrently within the structured scope
            val jobs = ready.map { blockId ->
                val block = graph.blocks.find { it.id == blockId }!!
                scope.async {
                    executeBlock(release, block, statusMap, outputsMap)
                }
            }

            // Wait for this wave to complete
            jobs.forEach { it.await() }
        }

        // Mark any remaining blocks (whose predecessors failed) as FAILED
        for (blockId in remaining) {
            statusMap[blockId] = BlockStatus.FAILED
            val execution = BlockExecution(
                blockId = blockId,
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = "Skipped: predecessor failed",
            )
            repository.upsertBlockExecution(execution)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, execution))
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

    private suspend fun executeContainer(
        release: Release,
        container: Block.ContainerBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        val startTime = Clock.System.now()
        statusMap[container.id] = BlockStatus.RUNNING
        val runningExec = BlockExecution(
            blockId = container.id,
            releaseId = release.id,
            status = BlockStatus.RUNNING,
            startedAt = startTime,
        )
        repository.upsertBlockExecution(runningExec)
        emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, runningExec))

        try {
            val childGraph = container.children
            if (childGraph.blocks.isEmpty()) {
                statusMap[container.id] = BlockStatus.SUCCEEDED
                val succeededExec = BlockExecution(
                    blockId = container.id,
                    releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    startedAt = startTime,
                    finishedAt = Clock.System.now(),
                )
                repository.upsertBlockExecution(succeededExec)
                emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, succeededExec))
                return
            }

            val sorted = DagTopologicalSort.sort(childGraph)
                ?: throw IllegalStateException("Container sub-DAG contains a cycle")

            val childPredecessors = buildPredecessorMap(childGraph)
            val childStatusMap = ConcurrentHashMap<BlockId, BlockStatus>()
            val childOutputsMap = ConcurrentHashMap<BlockId, Map<String, String>>()

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

            // Use coroutineScope for structured concurrency within the container
            coroutineScope {
                executeWaves(this, release, childGraph, sorted, childPredecessors, childStatusMap, childOutputsMap)
            }

            // Propagate child outputs to parent scope
            outputsMap.putAll(childOutputsMap)
            statusMap.putAll(childStatusMap)

            val allChildrenSucceeded = childStatusMap.values.all { it == BlockStatus.SUCCEEDED }
            val containerStatus = if (allChildrenSucceeded) BlockStatus.SUCCEEDED else BlockStatus.FAILED

            statusMap[container.id] = containerStatus
            val containerExec = BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = containerStatus,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            )
            repository.upsertBlockExecution(containerExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, containerExec))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            statusMap[container.id] = BlockStatus.FAILED
            val failedExec = BlockExecution(
                blockId = container.id,
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = e.message,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            )
            repository.upsertBlockExecution(failedExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, failedExec))
        }
    }

    private suspend fun executeAction(
        release: Release,
        block: Block.ActionBlock,
        statusMap: MutableMap<BlockId, BlockStatus>,
        outputsMap: MutableMap<BlockId, Map<String, String>>,
    ) {
        val startTime = Clock.System.now()
        statusMap[block.id] = BlockStatus.RUNNING
        val runningExec = BlockExecution(
            blockId = block.id,
            releaseId = release.id,
            status = BlockStatus.RUNNING,
            startedAt = startTime,
        )
        repository.upsertBlockExecution(runningExec)
        emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, runningExec))

        try {
            val outputs: Map<String, String>

            if (block.type == BlockType.USER_ACTION) {
                statusMap[block.id] = BlockStatus.WAITING_FOR_INPUT
                val waitingExec = BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.WAITING_FOR_INPUT,
                    startedAt = startTime,
                )
                repository.upsertBlockExecution(waitingExec)
                emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, waitingExec))

                val deferred = CompletableDeferred<Map<String, String>>()
                pendingApprovals
                    .getOrPut(release.id) { ConcurrentHashMap() }[block.id] = deferred

                outputs = deferred.await()
                pendingApprovals[release.id]?.remove(block.id)
            } else {
                val resolvedParams = TemplateEngine.resolveParameters(
                    block.parameters,
                    release.parameters,
                    outputsMap,
                )

                val connections = mutableMapOf<ConnectionId, ConnectionConfig>()
                block.connectionId?.let { connId ->
                    val connection = connectionsRepository.findById(connId)
                    if (connection != null) {
                        connections[connId] = connection.config
                    }
                }

                val context = ExecutionContext(
                    releaseId = release.id,
                    parameters = release.parameters,
                    blockOutputs = outputsMap,
                    connections = connections,
                )

                val timeoutMs = block.timeoutSeconds?.let { it * 1000 }
                outputs = if (timeoutMs != null) {
                    withTimeout(timeoutMs) {
                        blockExecutor.execute(block, resolvedParams, context)
                    }
                } else {
                    blockExecutor.execute(block, resolvedParams, context)
                }
            }

            outputsMap[block.id] = outputs
            statusMap[block.id] = BlockStatus.SUCCEEDED
            val succeededExec = BlockExecution(
                blockId = block.id,
                releaseId = release.id,
                status = BlockStatus.SUCCEEDED,
                outputs = outputs,
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            )
            repository.upsertBlockExecution(succeededExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, succeededExec))
        } catch (e: CancellationException) {
            if (!restartingReleases.contains(release.id)) {
                statusMap[block.id] = BlockStatus.FAILED
                val cancelledExec = BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.FAILED,
                    error = "Cancelled",
                    startedAt = startTime,
                    finishedAt = Clock.System.now(),
                )
                repository.upsertBlockExecution(cancelledExec)
                emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, cancelledExec))
            }
            throw e
        } catch (e: Exception) {
            statusMap[block.id] = BlockStatus.FAILED
            val failedExec = BlockExecution(
                blockId = block.id,
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = e.message ?: "Unknown error",
                startedAt = startTime,
                finishedAt = Clock.System.now(),
            )
            repository.upsertBlockExecution(failedExec)
            emitEvent(ReleaseEvent.BlockExecutionUpdated(release.id, failedExec))
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
