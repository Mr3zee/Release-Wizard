# Execution Engine Issues

18 findings: 3 Critical, 7 High, 4 Medium, 4 Low

## Critical

### ✅ EXEC-C1: Wave-loop busy-polling causes artificial serialization

**Files:** `ExecutionEngine.kt:974-984`

`runWaveLoop` launches one wave at a time and awaits all blocks before picking the next wave. When blocks are in RUNNING or WAITING_FOR_INPUT, the loop busy-polls with `delay(100.milliseconds)`. A DAG node whose predecessors are all done but shares a topological level with a slow sibling must wait for that sibling to finish. This causes unnecessary serialization of unrelated blocks and wastes a coroutine spinning every 100ms for the entire duration of any long-running block.

**Fix:** Replace the wave-based approach with an event-driven model where each block is launched as a coroutine the moment its predecessors succeed. Use a `Channel` or `CompletableDeferred` that fires when a block transitions out of its active state, replacing the poll entirely.

---

### ✅ EXEC-C2: Race between `startExecution` and `registerJob` — ghost executions

**Files:** `ExecutionEngine.kt:125-133`

`scope.launch` immediately schedules the coroutine, but `registerJob` is called *after* `launch`. If `cancelExecution` or `stopReleaseInternal` is called on another thread between `launch` and `registerJob`, `activeJobs[releaseId]` is null and the cancel succeeds without stopping the newly launched job. The job then continues executing with no entry in `activeJobs`, making it invisible to all subsequent operations.

**Fix:** Register the job atomically with its creation. Either use a mutex to guard `launch` + `registerJob`, or pre-allocate a placeholder in `activeJobs` before launching.

---

### ✅ EXEC-C3: DB error in `completeBlockSuccess` silently marks SUCCEEDED block as FAILED

**Files:** `ExecutionEngine.kt:1103-1138`

`executeWithBlockErrorHandling` catches all non-cancellation exceptions, records FAILED status, and returns normally. If `completeBlockSuccess` (called inside the try) throws a DB error, it is caught by this same handler and the block is re-marked as FAILED — losing the original successful result and outputs. This is a data-loss bug.

**Fix:** Separate the success-persistence path from the execution path. Catch DB errors from `completeBlockSuccess` specifically and handle them differently (retry or propagate) rather than treating them as block execution failures.

---

## High

### EXEC-H1: GitHub run-ID discovery picks wrong run under concurrent dispatches

**Files:** `GitHubActionExecutor.kt:142-168`

After dispatching a workflow, the executor fetches `?per_page=1` (most recent run) with no timestamp comparison, ref filtering, or correlation ID. If two simultaneous releases trigger the same workflow, each discovers the same run ID. Both will then poll the same external run and report identical results.

**Fix:** Add a `created_at` minimum filter based on the dispatch timestamp, and filter by `head_branch` matching the dispatched ref.

---

### EXEC-H2: `cancelExecution` bypasses `emitCompletionOnce` — duplicate WebSocket events

**Files:** `ExecutionEngine.kt:117-123, 139-173`

`cancelExecution` directly calls `emitEvent(ReleaseStatusChanged)` and `emitEvent(ReleaseCompleted)` without going through `emitCompletionOnce`. If the coroutine cancel propagates before `cancelExecution`'s own emit, both code paths emit CANCELLED events, corrupting client state machines.

**Fix:** Route `cancelExecution`'s completion events through `emitCompletionOnce`.

---

### EXEC-H3: Stop/restart clear `replayBuffers` before emitting events — breaks WS sequence continuity

**Files:** `ExecutionEngine.kt:470-505, 392-394`

`stopReleaseInternal` clears all per-release in-memory state before emitting WebSocket events. The emitted events recreate the buffer with sequence numbers starting from 1, creating a gap for reconnecting subscribers.

**Fix:** Emit the stop/restart events before clearing the replay state, or include a "sequence reset" event.

---

### EXEC-H4: `resumeAction` loses original `startedAt` — duration data corrupted on recovery

**Files:** `ExecutionEngine.kt:307-338, 361`

`resumeAction` passes `Clock.System.now()` as `startTime`, ignoring the persisted `startedAt`. Duration metrics are permanently wrong after any restart.

**Fix:** Use `persistedExec.startedAt ?: Clock.System.now()` consistently in all recovery paths.

---

### EXEC-H5: SSRF check missing at execution time — only validated at connection-test time

**Files:** `TeamCityBuildExecutor.kt:113`, `TeamCityArtifactService.kt:58`, `BuildPollingService.kt:157`, `SlackMessageExecutor.kt:57`

`validateUrlNotPrivate()` is called only when a connection is *tested*, not during actual block execution. All executors use connection URLs directly with no SSRF validation at runtime.

**Fix:** Call `validateUrlNotPrivate()` at the start of each executor's `execute()` and `resume()` methods.

---

### EXEC-H6: TLS certificate verification not explicitly configured for CIO HttpClient

**Files:** `AppModule.kt`

The shared `HttpClient(CIO)` uses default settings. The CIO engine may not verify TLS certificates by default, enabling silent MITM attacks.

**Fix:** Explicitly configure TLS verification in the HttpClient engine configuration.

---

### EXEC-H7: No concurrency cap on parallel DAG block execution

**Files:** `ExecutionEngine.kt:988-994`

`runWaveLoop` launches all "ready" blocks concurrently with no semaphore. A pipeline with 500 parallel action blocks spawns 500 concurrent coroutines.

**Fix:** Introduce a per-release or global `Semaphore` (e.g., 50 parallel blocks).

---

## Medium

### EXEC-M1: No maximum total wait time for build polling

**Files:** `BuildPollingService.kt:51-87, 307-362`

Once a build transitions to RUNNING, there is no upper bound on polling duration. A stuck external build blocks its coroutine indefinitely.

**Fix:** Add a configurable global maximum poll duration (e.g., 4 hours).

---

### EXEC-M2: `CancellationException` swallowed in `TeamCityArtifactService.fetchRecursive`

**Files:** `TeamCityArtifactService.kt:43-95`

The recursive HTTP calls catch `Exception` which includes `CancellationException`. Release cancellation does not abort deep artifact traversal.

**Fix:** Add `if (e is CancellationException) throw e` at the top of the catch block.

---

### EXEC-M3: `ExecutionContext.blockOutputs` exposes live mutable map to executors

**Files:** `ExecutionEngine.kt:1033`

The `blockOutputs` map reference is shared with executors. The interface does not enforce immutability.

**Fix:** Pass an immutable snapshot (`outputsMap.toMap()`) to the execution context.

---

### EXEC-M4: O(n^2) linear scan for block lookup in wave loop

**Files:** `ExecutionEngine.kt:989-990`

`graph.blocks.find { it.id == blockId }` is O(n) for each block in each wave.

**Fix:** Build a `blockId -> Block` map once before the loop.

---

## Low

### EXEC-L1: `discoverTeamCitySubBuilds` assigns sequential integers instead of real BFS levels

**Files:** `BuildPollingService.kt:118-125`

### EXEC-L2: `DROP_OLDEST` SharedFlow can drop `ReleaseCompleted` for slow WS subscribers

**Files:** `ExecutionEngine.kt:109-110`

### EXEC-L3: Token deactivated twice when `resume` falls through to `execute`

**Files:** `TeamCityBuildExecutor.kt:51-60, 133-137`

### EXEC-L4: Error body logging inconsistency across executors

**Files:** `GitHubPublicationExecutor.kt:99-101`, `TeamCityBuildExecutor.kt:122`
