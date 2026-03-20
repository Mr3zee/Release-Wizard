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

## ✅ Phase 5 — Audit, Observability & Correctness (COMPLETE)

All 19 Phase 5 issues fixed + 10 additional review findings. All 502 tests pass (444 existing + 58 new).
Reviewed by QA, Backend, Security, and Database experts — all must-fix findings addressed.

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

## ✅ Phase 6 — Polish & Low-Severity (COMPLETE)

29 issues fixed + 21 new tests. All 523 tests pass (502 existing + 21 new).
Reviewed by QA, Backend, Security, and Database experts — all must-fix findings addressed.

### Stream 6A: Auth Polish

**Scope:** `AuthService.kt`, `AuthRoutes.kt`, `UserSession.kt`, `PasswordValidator.kt`, `UserTable.kt`
**Changes:**
- AUTH-M1: Argon2 hash computed outside SERIALIZABLE transaction (400ms lock reduction) + unique constraint catch for concurrent registrations
- AUTH-L1: Removed `csrfToken = ""` default — token must be set explicitly at login/registration
- AUTH-L2: Unified login failure log messages to prevent log-based user enumeration
- AUTH-L3: Aligned `UserTable.username` varchar(255) → varchar(64) to match route validation
- AUTH-L5: Whitespace no longer counts as a special character in password validation

### Stream 6B: Connection Polish

**Scope:** `ConnectionsService.kt`, `ConnectionsRoutes.kt`
**Changes:**
- CONN-M2: Reject no-op PUT (both name and config null → 400)
- CONN-L3: Validate workflowFile format (alphanumeric + dots/hyphens only)
- CONN-L6: Removed dead `webhookUrl()` function and unused `WebhookConfig` import

### Stream 6C: Team Polish

**Scope:** `TeamService.kt`, `TeamRoutes.kt`, `TeamRepository.kt`
**Changes:**
- TEAM-M1: Hoisted audit/tag/teamAccess DI to route scope (was per-request inline injection)
- TEAM-M3: Reject both-null team update (name and description both null → 400)
- TEAM-L2: Generic error message for invite to prevent username enumeration (all failure paths return same message)
- TEAM-L3: Auto-cancel pending join request when user joins via invite (best-effort, try/catch)

### Stream 6D: Notification Polish

**Scope:** `NotificationService.kt`, `NotificationListener.kt`, `NotificationRepository.kt`
**Changes:**
- NOTIF-H2: Fixed empty userId ownership bypass — non-admins cannot delete system configs
- NOTIF-H3: Removed orphaned `update()` method from repository interface and implementation
- NOTIF-H6: `start()` now returns `Job` for lifecycle management
- NOTIF-M1: Re-throw `CancellationException` in event collection

### Stream 6E: Schedule Polish

**Scope:** `ScheduleService.kt`, `CronUtils.kt`
**Changes:**
- SCHED-H1: Reject schedule creation when `computeNextRun` returns null (schedule would never fire)
- SCHED-H2: Log warning for non-IAE exceptions in interval validation (was silent pass)
- Fixed duplicate audit logging in `create()`, `toggle()`, `delete()` (was producing 2 events per operation)

### Stream 6F: Infrastructure Polish

**Scope:** `RequestSizeLimit.kt`, `CorrelationId.kt`, `Application.kt`
**Changes:**
- INFRA-H1: Documented RequestSizeLimit pipeline behavior for chunked bodies
- INFRA-H4: Startup failures logged as CRITICAL for monitoring visibility
- INFRA-M2: CorrelationId reads upstream `X-Request-ID` header (validated with `[a-zA-Z0-9._-]` regex)
- INFRA-M7: Warns when SECURE_COOKIE is disabled
- INFRA-L2: LockConflictException response includes correlationId
- INFRA-L4: Root endpoint no longer discloses version

### Stream 6G: Maven, Webhook, Tags & Releases

**Scope:** `TriggerService.kt`, `MavenPollerService.kt`, `ReleaseWebSocketRoutes.kt`, `AuditRepository.kt`, `AuditEvent.kt`, `DatabaseFactory.kt`
**Changes:**
- MAVEN-H4: SecureRandom singleton in TriggerService (was per-call instantiation)
- MAVEN-L2: `stop()` properly cancels polling job
- REL-M6: WebSocket origin validation fails closed on parse errors + null originHost
- TAG-M6: Unknown enum values in audit events handled gracefully (`UNKNOWN` fallback instead of 500)
- TAG-L3: Composite index on `(team_id, tag)` for release_tags table

### Deferred Items (acceptable as-is)

- NOTIF-C1: Persistent notification queue — major redesign, deferred to backlog
- AUTH-M2, M3, M4, M6, M7: Already acceptable or infra-dependent
- CONN-H5, M4, M5: Marginal optimizations, acceptable limits
- SCHED-H3, M4: API redesign or needs separate design work
- INFRA-H3, M3, M4, M6, M8: Already configurable or marginal

---

## Execution Summary

```
Phase 1 (Critical)     — 7 parallel streams  — All Critical issues  ✅ COMPLETE
Phase 2 (Security)     — 4 parallel streams  — Auth, SSRF, AuthZ, Credentials  ✅ COMPLETE
Phase 3 (Validation)   — 3 parallel streams  — Input, Transactions, Schema  ✅ COMPLETE
Phase 4 (Resources)    — 3 parallel streams  — Rate limits, Bounds, HTTP/TLS  ✅ COMPLETE
Phase 5 (Correctness)  — 4 parallel streams  — Audit, WebSocket, Webhooks, Executors  ✅ COMPLETE
Phase 6 (Polish)       — 7 parallel streams  — All remaining Medium + Low  ✅ COMPLETE
```

Each phase's streams are fully independent and can be worked concurrently.
Phases should be executed in order (1 before 2, etc.) since later phases may depend on
infrastructure changes from earlier phases.
