# Server Issues — Remediation Plan

143+ findings across 12 modules. This plan groups fixes into parallelizable work streams executed in 6 phases.
Each phase contains independent work streams that can run concurrently.

## Issue Files

| Module | File | Critical | High | Medium | Low |
|--------|------|----------|------|--------|-----|
| Execution Engine | [SERVER_ISSUE_EXECUTION_ENGINE.md](server_issues/SERVER_ISSUE_EXECUTION_ENGINE.md) | 3 | 7 | 4 | 4 |
| Releases | [SERVER_ISSUE_RELEASES.md](server_issues/SERVER_ISSUE_RELEASES.md) | 2 | 3 | 7 | 3 |
| Schedules | [SERVER_ISSUE_SCHEDULES.md](server_issues/SERVER_ISSUE_SCHEDULES.md) | 2 | 4 | 8 | 0 |
| Auth | [SERVER_ISSUE_AUTH.md](server_issues/SERVER_ISSUE_AUTH.md) | 0 | 6 | 7 | 5 |
| Connections | [SERVER_ISSUE_CONNECTIONS.md](server_issues/SERVER_ISSUE_CONNECTIONS.md) | 2 | 6 | 5 | 6 |
| Projects | [SERVER_ISSUE_PROJECTS.md](server_issues/SERVER_ISSUE_PROJECTS.md) | 2 | 4 | 4 | 2 |
| Teams | [SERVER_ISSUE_TEAMS.md](server_issues/SERVER_ISSUE_TEAMS.md) | 2 | 5 | 7 | 3 |
| Notifications | [SERVER_ISSUE_NOTIFICATIONS.md](server_issues/SERVER_ISSUE_NOTIFICATIONS.md) | 2 | 4 | 4 | 2 |
| Webhooks | [SERVER_ISSUE_WEBHOOKS.md](server_issues/SERVER_ISSUE_WEBHOOKS.md) | 0 | 3 | 7 | 1 |
| Maven & Triggers | [SERVER_ISSUE_MAVEN_TRIGGERS.md](server_issues/SERVER_ISSUE_MAVEN_TRIGGERS.md) | 2 | 4 | 5 | 3 |
| Tags & Audit | [SERVER_ISSUE_TAGS_AUDIT.md](server_issues/SERVER_ISSUE_TAGS_AUDIT.md) | 2 | 4 | 6 | 3 |
| Infrastructure | [SERVER_ISSUE_INFRASTRUCTURE.md](server_issues/SERVER_ISSUE_INFRASTRUCTURE.md) | 2 | 4 | 8 | 6 |
| **Total** | | **21** | **54** | **72** | **38** |

---

## ✅ Phase 1 — Critical Race Conditions & Data Safety (COMPLETE)

All 16 Critical-severity issues fixed. All 382 tests pass (361 existing + 21 new).
Reviewed by QA, Backend, Security, and Database experts — all must-fix findings addressed.

### Stream 1A: Execution Engine Concurrency (EXEC-C1, C2, C3)

**Scope:** `ExecutionEngine.kt`
**Issues:**
- C2: Register job atomically with launch (pre-allocate in `activeJobs` before `scope.launch`)
- C3: Separate success-persistence from execution error handling (don't catch DB errors from `completeBlockSuccess` as block failures)
- C1: Replace wave-loop busy-poll with event-driven block scheduling (largest change — can be done incrementally: first fix the poll interval, then redesign)

### Stream 1B: Release Approval Mutex (REL-C1, C2)

**Scope:** `ReleasesService.kt`
**Issues:**
- C1: Move mutex removal to after `approveBlock` returns
- C2: Add lifecycle cleanup — clear mutex entries on release completion/cancellation

### Stream 1C: Scheduler Atomicity (SCHED-C1, C2)

**Scope:** `SchedulerService.kt`, `ScheduleRepository.kt`
**Issues:**
- C1: Wrap `fireSchedule` + `advanceNextRun` in a single transaction
- C2: Add `SELECT ... FOR UPDATE SKIP LOCKED` to `findDueSchedules`

### Stream 1D: Project Lock Atomicity (PROJ-C1, C2)

**Scope:** `ProjectsService.kt`, `ExposedProjectLockRepository.kt`
**Issues:**
- C1: Single transaction for lock check + project update
- C2: Replace delete-then-insert with upsert `INSERT ... ON CONFLICT DO UPDATE`

### Stream 1E: Team Membership TOCTOU (TEAM-C1, C2)

**Scope:** `TeamService.kt`, `TeamRepository.kt`
**Issues:**
- C1: Single `suspendTransaction` with `SELECT FOR UPDATE` for last-lead check + mutate
- C2: Single transaction for invite accept / join approve + addMember. Catch duplicate key as 409

### Stream 1F: Infrastructure Quick Wins (INFRA-C1, TAG-C1, TAG-C2)

**Scope:** `EncryptionService.kt`, `TeamRoutes.kt`, `TagService.kt`
**Issues:**
- INFRA-C1: Make `SecureRandom` a singleton field in `EncryptionService`
- TAG-C1: Replace `tagRepository` injection with `TagService` in team routes
- TAG-C2: Add `auditService.log()` calls for TAG_RENAMED / TAG_DELETED in TagService

### Stream 1G: Maven Poller Races (MAVEN-C1, C2)

**Scope:** `MavenPollerService.kt`, `ExposedMavenTriggerRepository.kt`
**Issues:**
- C1: Fixed-rate ticker (measure from cycle start, not end)
- C2: `SELECT ... FOR UPDATE SKIP LOCKED` for trigger polling

---

## ✅ Phase 2 — Security Hardening (High Priority) (COMPLETE)

All 20 Phase 2 issues fixed + 4 additional review findings. All 406 tests pass (382 existing + 24 new).
Reviewed by QA, Backend, Security, and Database experts — all must-fix findings addressed.

### Stream 2A: SSRF Hardening (CONN-C1, CONN-C2, CONN-H2, EXEC-H5, NOTIF-H1, MAVEN-H1)

**Scope:** `ConnectionTester.kt`, `SsrfProtectionPlugin.kt` (new), `AppModule.kt`, `NotificationListener.kt`, `NotificationService.kt`, `MavenMetadataFetcher.kt`
**Changes:**
- CONN-C1: `SsrfProtection` HttpClient plugin validates every outgoing request; `validateHostNotPrivate` with IPv6 ULA (fc00::/7) support
- CONN-H2: Slack connection test validates URL against SSRF
- EXEC-H5: All executors protected via HttpClient plugin (defense-in-depth)
- NOTIF-H1: Webhook URL validated on create (HTTPS required, no private IPs) + re-validated at send time
- MAVEN-H1: Maven repo URLs re-validated at poll time
- CONN-C2: GitHub YAML fetches capped at 512 KB via `json["size"]` check

### Stream 2B: Auth & Session Security (AUTH-H1–H6, AUTH-M5, INFRA-H2)

**Scope:** `AuthService.kt`, `AuthRoutes.kt`, `AccountLockoutService.kt` (new), `CsrfPlugin.kt`, `SessionTtlPlugin.kt`, `Config.kt`, `AuthModule.kt`
**Changes:**
- AUTH-H6: CSRF fails closed on empty session token
- INFRA-H2: Constant-time CSRF comparison with length-safe padding
- AUTH-H4: Per-username account lockout (5 attempts → 15s–15min exponential backoff, stale eviction at 10K entries)
- AUTH-H5: Argon2 parallelism raised to p=4 (OWASP minimum)
- AUTH-H3: Thread-safety verified and documented (argon2-jvm uses stateless JNI calls)
- AUTH-H1: `/me` moved inside `authenticate("session-auth")` block
- AUTH-H2: `requireAdminSession` replaced with `userSession()` + role check; TeamRepository injected at route level
- AUTH-M5: Absolute session lifetime (7 days default) via `absoluteSessionLifetimeSeconds` config

### Stream 2C: Authorization Gaps (REL-H2, REL-M4, TEAM-H1)

**Scope:** `ReleasesService.kt`, `ReleasesRoutes.kt`, `ReleaseWebSocketRoutes.kt`
**Changes:**
- REL-H2: `getRelease`/`getBlockExecutions` now require session + access check (all callers updated)
- REL-M4: Cancel/archive/delete require TEAM_LEAD role (via `checkAccessTeamLead`)
- TEAM-H1: Last-lead invariant enforced via Phase 1 atomic DB operations (verified with tests)

### Stream 2D: Credential Exposure (NOTIF-H4, HOOK-M2, CONN-M1, CONN-H4)

**Scope:** `NotificationService.kt`, `WebhookRoutes.kt`, `ConnectionsRoutes.kt`, `ConnectionsService.kt`
**Changes:**
- NOTIF-H4: Webhook URLs masked in API responses (first 20 chars + `****`)
- HOOK-M2: Bearer tokens masked in log output (first 8 chars + `...`)
- CONN-M1: Exception messages sanitized — generic errors to clients, details in server logs only
- CONN-H4: Audit log added for `updateConnection` (teamId fetched before update to prevent TOCTOU)

### Additional Review Fixes

- BUG-6: Webhook payload size enforced via `receiveText()` + length check (handles chunked transfer)
- BUG-7: WebSocket origin check fails closed when Origin present but Host missing
- S7: Maven metadata response capped at 512 KB
- S12: Deleted connections detected at release start (`validateConnectionTeamConsistency`)
- Slack HTTP response status checked in `NotificationListener`

---

## ✅ Phase 3 — Data Integrity & Validation (COMPLETE)

All 22 Phase 3 issues fixed + 8 additional review findings. All 433 tests pass (406 existing + 27 new).
Reviewed by QA, Backend, Security, and Database experts — all must-fix findings addressed.

Input validation, DAG validation, and DB consistency. All streams independent.

### Stream 3A: Input Validation Sweep

**Scope:** All route handlers, services, DTOs
**Issues:**
- PROJ-H2: Call `DagValidator.validate()` on project create and update
- PROJ-H1: Call `validateConnectionTeamConsistency` on update
- PROJ-H4: Move blank-name validation to service layer
- TAG-H4: Tag name length/charset validation + max list size
- REL-M7: Size bounds on parameters, tags, approval input
- SCHED-M8: Cap schedule parameter list size and value lengths
- CONN-M3: Blank-name validation on connection update
- CONN-H1: Fix encryption key validation (decode Base64 at config load time)

### Stream 3B: DB Transaction Consistency

**Scope:** All Exposed repositories
**Issues:**
- TAG-H1: SERIALIZABLE isolation for renameTag
- TAG-H2: Proper isolation for setTagsForRelease
- TAG-H3: Add teamId filter to renameTag deduplication query
- CONN-H6: Unify delete into single transactional method
- HOOK-H3: Deactivate existing token inside `create` transaction
- HOOK-M5: Combine token validation + status update in single transaction
- PROJ-H3: Window function for findAllWithCount
- TAG-M2: Window function for audit findByTeam

### Stream 3C: Unique Constraints & Schema

**Scope:** Table definitions
**Issues:**
- TEAM-H2: Partial unique index on invites `WHERE status = 'PENDING'`
- TEAM-H3: Same for join requests
- TEAM-H4: Membership check inside approve transaction
- HOOK-M4: Partial unique index on webhook tokens `WHERE active = true`
- TEAM-H5: Add `expiresAt` column to `TeamInviteTable`
- INFRA-C2: Evaluate migration strategy (Flyway/Liquibase or createMissingTablesAndColumns)

---

## ✅ Phase 4 — Resource Management & DoS Prevention (COMPLETE)

All 16 Phase 4 issues fixed + 6 additional review findings. All 444 tests pass (433 existing + 11 new).
Reviewed by QA, Backend, Security, and Database experts — all must-fix findings addressed.

### Stream 4A: Rate Limiting & Caps

**Scope:** `Application.kt`, route files, service files
**Changes:**
- REL-H3: Per-user WebSocket connection limit (50) with proper try/finally cleanup and map entry eviction
- REL-H4: Bounded WS channel capacity (1024) with trySend backpressure
- HOOK-M1: Per-IP rate limit (30/min) on `/webhooks/status`
- MAVEN-M1: Per-IP rate limit (30/min) on trigger webhook endpoint
- TEAM-M7: Per-IP rate limit (10/min) on invite/join-request creation
- SCHED-H4: Per-project schedule count cap (20)
- MAVEN-M2: Per-project trigger count cap (20)
- NOTIF-M2: Per-project notification config limit (20)

### Stream 4B: Execution Engine Resource Bounds

**Scope:** `ExecutionEngine.kt`, `BuildPollingService.kt`, `TeamCityArtifactService.kt`
**Changes:**
- EXEC-H7: Concurrency semaphore (50) around actual block execution (not gate waits) to prevent deadlock
- EXEC-M1: Global max poll duration (4 hours) for TC and GH polling
- EXEC-M2: Re-throw CancellationException in artifact fetcher and Maven metadata fetcher
- MAVEN-M3: `withTimeoutOrNull(4min)` around `pollAllTriggers()`
- MAVEN-M5: 5s timeout for Maven metadata fetch during trigger creation

### Stream 4C: HTTP Client & TLS

**Scope:** `AppModule.kt`, `MavenMetadataFetcher.kt`, `DatabaseFactory.kt`
**Changes:**
- EXEC-H6: Explicit TLS verification configuration for CIO HttpClient
- MAVEN-H2: Already addressed in Phase 2 (S7: 512 KB cap)
- INFRA-M1: HikariCP keepaliveTime (5min), idleTimeout (10min), connectionTestQuery

### Additional Review Fixes

- WS counter leak: Wrapped all post-increment code in try/finally to prevent counter leak on early exit paths
- Rate limiters: Added per-IP `requestKey` to all rate limiters (were global buckets)
- Semaphore placement: Moved from runWaveLoop to resolveAndExecute to avoid holding during gate waits
- NotificationService: Moved access check before cap check and SSRF validation
- CancellationException: Added re-throw in MavenMetadataFetcher.fetch()
- WS map cleanup: Entries removed when counter reaches 0

---

## Phase 5 — Audit, Observability & Correctness

Missing audit logs, event ordering, error handling. All streams independent.

### Stream 5A: Audit Coverage

**Scope:** All service files, `AuditService.kt`
**Issues:**
- PROJ-M3: Audit `updateProject`
- CONN-H4: Audit `updateConnection`
- SCHED-M5: Audit schedule CRUD
- SCHED-M6: Audit scheduled release starts (actor attribution)
- TEAM-M5: Audit ADMIN bypass reads
- TAG-M1: Sanitize user-controlled data in audit details
- TAG-M4: Move AuditService to dedicated Koin module

### Stream 5B: WebSocket Event Correctness

**Scope:** `ExecutionEngine.kt`, `ReleasesService.kt`
**Issues:**
- EXEC-H2: Route `cancelExecution` through `emitCompletionOnce`
- EXEC-H3: Emit events before clearing replay buffers on stop/restart
- EXEC-H4: Use persisted `startedAt` in all recovery paths
- REL-M1: Move status checks inside engine (uses existing mutexes)
- REL-M2: Include WAITING blocks in batch stop

### Stream 5C: Webhook Token Lifecycle

**Scope:** `StatusWebhookService.kt`, `WebhookRoutes.kt`
**Issues:**
- HOOK-H1: Deactivate token on first successful use
- HOOK-H2: Distinct response for non-RUNNING blocks (410 Gone)
- HOOK-M7: Periodic cleanup of expired tokens
- HOOK-M6: Normalize baseUrl trailing slash

### Stream 5D: Executor Correctness

**Scope:** `GitHubActionExecutor.kt`, `BuildPollingService.kt`
**Issues:**
- EXEC-H1: GitHub run-ID discovery with timestamp + ref filter
- EXEC-M3: Immutable blockOutputs snapshot for executors
- EXEC-M4: Pre-build blockId -> Block lookup map
- REL-M5: Re-validate connection consistency on rerun

---

## Phase 6 — Polish & Low-Severity

Remaining Medium and Low items. Can be worked as a backlog.

### Stream 6A: Auth Polish

- AUTH-M1: Dummy hash outside SERIALIZABLE transaction
- AUTH-M2: Make `updateUserRole` private
- AUTH-M3: Hoist TeamRepository injection to function scope
- AUTH-M4: Reduce session refresh threshold or force invalidation on role change
- AUTH-M6: Configure ForwardedHeaders for rate limiter
- AUTH-M7: Explicit CORS disabled path
- AUTH-L1 through L5

### Stream 6B: Connection Polish

- CONN-M2: Reject no-op PUT
- CONN-M4: GitHub workflow pagination
- CONN-M5: TeamCity build-type pagination
- CONN-H5: Single query for access check + findById
- CONN-L1 through L6

### Stream 6C: Team Polish

- TEAM-M1: Move /audit and /tags logic to TeamService
- TEAM-M2: Add session to listTeams
- TEAM-M3: Reject both-null update
- TEAM-M4: Lightweight findMembershipRole method
- TEAM-M6: Extend pre-deletion check for FK violations
- TEAM-L1 through L3

### Stream 6D: Notification Polish

- NOTIF-C1: Persistent notification queue (larger redesign)
- NOTIF-H5: Check Slack response status
- NOTIF-H6: Return Job from listener start()
- NOTIF-M1: Re-throw CancellationException
- NOTIF-M3: Scope list response to caller's configs
- NOTIF-M4: Derive type from config discriminator
- NOTIF-H2: Fix ownership check logic
- NOTIF-H3: Delete orphaned update() method

### Stream 6E: Schedule Polish

- SCHED-H1: Reject null nextRunAt on create
- SCHED-H2: Remove blanket catch in validateMinimumInterval
- SCHED-H3: Optional wrapper for nullable update fields
- SCHED-M1 through M4

### Stream 6F: Infrastructure Polish

- INFRA-H1: Fix RequestSizeLimit (finish pipeline + Netty maxContentLength)
- INFRA-H3: Lazy session refresh (only role-sensitive endpoints)
- INFRA-H4: Fatal startup on critical service failure
- INFRA-M2 through M8
- INFRA-L1 through L6

### Stream 6G: Maven & Webhook Polish

- MAVEN-H3: Constant-time webhook timing
- MAVEN-H4: SecureRandom singleton
- MAVEN-M4: Idempotency for version fires
- MAVEN-L1, L2, M6, M7
- HOOK-M3: Chunked payload protection
- HOOK-L1
- REL-M6: WebSocket Origin validation
- TAG-M3, M5, M6, L1-L3

---

## Execution Summary

```
Phase 1 (Critical)     — 7 parallel streams  — All Critical issues  ✅ COMPLETE
Phase 2 (Security)     — 4 parallel streams  — Auth, SSRF, AuthZ, Credentials  ✅ COMPLETE
Phase 3 (Validation)   — 3 parallel streams  — Input, Transactions, Schema  ✅ COMPLETE
Phase 4 (Resources)    — 3 parallel streams  — Rate limits, Bounds, HTTP/TLS  ✅ COMPLETE
Phase 5 (Correctness)  — 4 parallel streams  — Audit, WebSocket, Webhooks, Executors
Phase 6 (Polish)       — 7 parallel streams  — All remaining Medium + Low
```

Each phase's streams are fully independent and can be worked concurrently.
Phases should be executed in order (1 before 2, etc.) since later phases may depend on
infrastructure changes from earlier phases.
