# Releases Issues

13 findings: 2 Critical, 3 High, 7 Medium, 3 Low

## Critical

### ✅ REL-C1: `approveBlock` removes mutex before `executionEngine.approveBlock` completes

**Files:** `ReleasesService.kt:374-376`

The mutex is removed from `approvalMutexes` *before* `executionEngine.approveBlock` is invoked. A second concurrent approval request arriving between these two lines will create a fresh Mutex, bypassing the intended serialization.

**Fix:** Remove the mutex *after* `approveBlock` returns, or don't remove it here and rely on lifecycle cleanup.

---

### ✅ REL-C2: `approvalMutexes` grows unbounded — memory leak

**Files:** `ReleasesService.kt`

`approvalMutexes` is a `ConcurrentHashMap<Pair<String,String>, Mutex>`. After the `ThresholdMet` path removes the key, `Recorded` and `Rejected` paths never remove it. Leaks memory indefinitely across releases.

**Fix:** Add lifecycle cleanup — remove entries when a release completes, is cancelled, or when the block moves to a terminal state.

---

## High

### REL-H1: `findByStatuses` recovery query silently caps at 1000 rows

**Files:** `ExposedReleasesRepository.kt:208-215`

A hard limit of 1000 rows with no pagination. Recovery silently misses releases past the 1000-row mark.

**Fix:** Remove the hard limit or add pagination with a loop.

---

### ✅ REL-H2: `getRelease`/`getBlockExecutions` are authorization-free public methods

**Files:** `ReleasesService.kt:107-113`

Neither method performs authorization checks. Currently safe because callers check access first, but the public interface is a design fragility.

**Fix:** Add `session: UserSession` parameters and enforce `checkAccess` inside them.

---

### REL-H3: No per-user WebSocket connection limit — DoS via memory exhaustion

**Files:** `ReleaseWebSocketRoutes.kt`

No cap on simultaneous WebSocket connections per user. Each allocates unbounded `Channel<ReleaseEvent>(Channel.UNLIMITED)`.

**Fix:** Track connections per user ID and reject above a threshold (e.g., 50).

---

## Medium

### REL-H4: `Channel.UNLIMITED` per WebSocket connection — unbounded memory growth

**Files:** `ReleaseWebSocketRoutes.kt:85`

Slow consumers cause the channel buffer to grow without bound.

**Fix:** Replace with `Channel(capacity = 1024)` and `BufferOverflow.DROP_OLDEST`.

---

### REL-M1: Service-layer state transitions are TOCTOU

**Files:** `ReleasesService.kt` (multiple lifecycle methods)

Status checks happen against a stale DB read, then engine calls follow without locks. Concurrent requests both pass the guard.

**Fix:** Move status checks inside the engine (which has per-release mutexes), or use optimistic locking.

---

### REL-M2: `batchStopBlocks` does not update WAITING blocks to STOPPED

**Files:** `ExecutionEngine.kt:392-398`

WAITING blocks are left in WAITING while the release moves to STOPPED, causing double-resume on resume.

**Fix:** Include WAITING blocks in the stop batch.

---

### REL-M3: `cancelRelease` does not audit-log when `findTeamId` lookup fails

**Files:** `ReleasesService.kt:215-226`

Engine cancels the release, but if findTeamId returns null, no audit entry is written.

---

### ✅ REL-M4: No team-role distinction for destructive operations

**Files:** `ReleasesService.kt:386-390`

Any team member can stop, cancel, archive, delete, or restart blocks. Only Team lead must be able to do so.

**Fix:** Introduce TEAM_LEAD checks for destructive operations.

---

### REL-M5: `rerunRelease` skips `validateConnectionTeamConsistency` on stale snapshot

**Files:** `ReleasesService.kt:197-202`

Rerun reuses the original DAG snapshot without re-validating connection team consistency.

**Fix:** Call `validateConnectionTeamConsistency` inside `rerunRelease`.

---

### REL-M6: WebSocket Origin validation incomplete

**Files:** `ReleaseWebSocketRoutes.kt:33-41`

Missing Origin header bypasses the check. Port is stripped from comparison.

---

### REL-M7: No size bounds on `parameters`, `tags`, or approval `input`

**Files:** `ReleasesRoutes.kt`, `ReleaseDtos.kt`

All unbounded with no length constraints.

**Fix:** Validate sizes in route handlers.

---

## Low

### REL-L1: Partial failure between create, apply tags, and engine start leaves orphan records
### REL-L2: `ReleaseTable.createdAt` is nullable with no reason
### REL-L3: Release existence oracle via error message ordering
