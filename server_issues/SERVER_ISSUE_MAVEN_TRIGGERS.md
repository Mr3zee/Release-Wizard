# Maven Publication & Triggers Issues

14 findings: 2 Critical, 4 High, 5 Medium, 3 Low

## Critical

### ✅ MAVEN-C1: Polling drift accumulates unboundedly

**Files:** `MavenPollerService.kt`

`delay(POLL_INTERVAL)` after completion. Effective rate collapses under slow repos.

**Fix:** Fixed-rate ticker measuring from cycle start.

---

### ✅ MAVEN-C2: Race condition in multi-instance — duplicate version fires

**Files:** `MavenPollerService.kt`, `ExposedMavenTriggerRepository.kt`

Two instances read same snapshot, both fire for same version.

**Fix:** `SELECT ... FOR UPDATE SKIP LOCKED` or unique constraint on `(triggerId, version)`.

---

## High

### ✅ MAVEN-H1: SSRF — stored `repoUrl` polled forever without re-validation
### ✅ MAVEN-H2: No response body size cap on Maven metadata fetches
### MAVEN-H3: Webhook timing side-channel in `fireWebhook`
### ✅ MAVEN-H4: `SecureRandom` instantiated per-call in `generateSecret`

---

## Medium

### ✅ MAVEN-M1: No rate limit on unauthenticated webhook endpoint
### ✅ MAVEN-M2: No per-project trigger count cap
### ✅ MAVEN-M3: `pollAllTriggers()` has no upper bound on total duration
### MAVEN-M4: Partial persistence — no idempotency on version fires
### ✅ MAVEN-M5: Live network fetch synchronously in create handler

---

## Low

### MAVEN-L1: Webhook secret stored as plain SHA-256
### ✅ MAVEN-L2: `stop()` does not await job completion
### MAVEN-M6/M7: `http://` allowed, no URL normalization
