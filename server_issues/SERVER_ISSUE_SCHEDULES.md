# Schedules Issues

14 findings: 2 Critical, 4 High, 8 Medium

## Critical

### SCHED-C1: Non-atomic `fireSchedule` + `advanceNextRun` — double-fire on crash

**Files:** `SchedulerService.kt`

`fireSchedule` creates a release, then `advanceNextRun` writes `lastRunAt` — as two separate operations. Crash between them causes double-fire on restart. No idempotency guard.

**Fix:** Write `lastRunAt` inside the same transaction as the release creation, or use CAS on `lastRunAt >= nextRunAt`.

---

### SCHED-C2: No distributed lock — duplicate fires in multi-instance deployments

**Files:** `SchedulerService.kt`

`findDueSchedules` is a plain SELECT with no `FOR UPDATE SKIP LOCKED`. Two instances fire the same schedule simultaneously.

**Fix:** Use `SELECT ... FOR UPDATE SKIP LOCKED` or a database advisory lock.

---

## High

### SCHED-H1: `computeNextRun` returns null silently — schedule stored but never fires

**Files:** `CronUtils.kt:28-30`, `ScheduleService.kt:52`

`computeNextRun` swallows exceptions and returns null. Schedule stored with `nextRunAt = null` will never execute. User gets 201 Created with no error.

**Fix:** Reject creation with 400 if `computeNextRun` returns null.

---

### SCHED-H2: `validateMinimumInterval` silently passes on non-IAE exceptions

**Files:** `CronUtils.kt:57-61`

Blanket `catch (_: Exception)` bypasses the rate-limit check if the cron library throws non-IAE.

**Fix:** Remove blanket catch. Default to rejecting if interval cannot be computed.

---

### SCHED-H3: `update()` cannot clear `lastRunAt` or `nextRunAt` — null means "don't touch"

**Files:** `ExposedScheduleRepository.kt:93-95`

`null` is overloaded as "leave unchanged." No way to explicitly set fields to null.

**Fix:** Use an `Optional`-style wrapper or separate parameters.

---

### SCHED-H4: No per-project schedule count cap

**Files:** `ScheduleService.kt:39-63`, `SchedulerService.kt:67-80`

Unbounded schedule creation can saturate the execution engine.

**Fix:** Enforce a configurable maximum per project (e.g., 10-20).

---

## Medium

### SCHED-M1: Polling interval drift
### SCHED-M2: `pollingJob` var not thread-safe
### SCHED-M3: Recovery blocks polling; no exception guard
### SCHED-M4: Silent retry of permanently broken schedules
### SCHED-M5: No audit trail for schedule CRUD operations
### SCHED-M6: Scheduled releases bypass audit actor attribution
### SCHED-M7: No write-role enforcement — any team member can create/toggle/delete
### SCHED-M8: Unbounded parameter injection into scheduled releases
