package com.github.mr3zee.execution

import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.dag.DagTopologicalSort
import com.github.mr3zee.dag.DagValidator
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleasesRepository
import com.github.mr3zee.template.TemplateEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Active release jobs, keyed by release ID
    private val activeJobs = ConcurrentHashMap<ReleaseId, Job>()

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

    fun startExecution(release: Release): Job {
        val job = scope.launch {
            executeRelease(release)
        }
        activeJobs[release.id] = job
        job.invokeOnCompletion {
            activeJobs.remove(release.id)
            pendingApprovals.remove(release.id)
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
            val now = Clock.System.now()
            emitEvent(ReleaseEvent.ReleaseStatusChanged(releaseId, ReleaseStatus.CANCELLED, finishedAt = now))
            emitEvent(ReleaseEvent.ReleaseCompleted(releaseId, ReleaseStatus.CANCELLED, finishedAt = now))
        }
    }

    suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, input: Map<String, String>): Boolean {
        val approvalMap = pendingApprovals[releaseId] ?: return false
        val deferred = approvalMap[blockId] ?: return false
        deferred.complete(input)
        return true
    }

    fun restartBlock(releaseId: ReleaseId, blockId: BlockId) {
        // Restart is handled by re-running the full execution loop.
        // The main loop reads block statuses from DB, so resetting to WAITING
        // in the service layer + re-executing is sufficient.
        // TODO: Full restart support in a future phase.
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
                val now = Clock.System.now()
                emitEvent(ReleaseEvent.ReleaseStatusChanged(release.id, ReleaseStatus.FAILED, finishedAt = now))
                emitEvent(ReleaseEvent.ReleaseCompleted(release.id, ReleaseStatus.FAILED, finishedAt = now))
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
            val finishedAt = Clock.System.now()
            emitEvent(ReleaseEvent.ReleaseStatusChanged(release.id, finalStatus, finishedAt = finishedAt))
            emitEvent(ReleaseEvent.ReleaseCompleted(release.id, finalStatus, finishedAt = finishedAt))

        } catch (e: CancellationException) {
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
            val now = Clock.System.now()
            emitEvent(ReleaseEvent.ReleaseStatusChanged(release.id, ReleaseStatus.CANCELLED, finishedAt = now))
            emitEvent(ReleaseEvent.ReleaseCompleted(release.id, ReleaseStatus.CANCELLED, finishedAt = now))
        } catch (e: Exception) {
            repository.setFinished(release.id, ReleaseStatus.FAILED)
            val now = Clock.System.now()
            emitEvent(ReleaseEvent.ReleaseStatusChanged(release.id, ReleaseStatus.FAILED, finishedAt = now))
            emitEvent(ReleaseEvent.ReleaseCompleted(release.id, ReleaseStatus.FAILED, finishedAt = now))
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

                val context = ExecutionContext(
                    releaseId = release.id,
                    parameters = release.parameters,
                    blockOutputs = outputsMap,
                    connections = emptyMap(), // Will be populated in Phase 6
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
