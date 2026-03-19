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

## Phase 1 — Critical Race Conditions & Data Safety

All Critical-severity issues that can cause data loss, duplicate operations, or ghost state.
These are the highest-risk items and should be addressed first.

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

## Phase 2 — Security Hardening (High Priority)

SSRF, auth bypass, CSRF, and credential exposure issues. All streams are independent.

### Stream 2A: SSRF Hardening

**Scope:** `ConnectionTester.kt`, all executors, `NotificationListener.kt`, `MavenPollerService.kt`
**Issues:**
- CONN-C1: Custom DNS resolver with private-IP re-check on every resolution
- CONN-H2: Add `validateUrlNotPrivate` to Slack test
- EXEC-H5: Call `validateUrlNotPrivate` at execution time in all executors
- NOTIF-H1: Validate notification webhook URLs (require https, reject private IPs)
- MAVEN-H1: Re-validate resolved IP at poll time
- CONN-C2: Size-cap GitHub YAML fetches (check `json["size"]`, cap at 512 KB)

### Stream 2B: Auth & Session Security

**Scope:** `AuthService.kt`, `AuthRoutes.kt`, `CsrfPlugin.kt`, `SessionTtlPlugin.kt`, `UserSession.kt`
**Issues:**
- AUTH-H6: CSRF fail-closed (reject empty token, remove default `""`)
- AUTH-H4: Per-username account lockout with exponential backoff
- AUTH-H5: Increase Argon2 parallelism to p=4
- AUTH-H3: Verify Argon2 thread safety or add Mutex
- AUTH-H1: Move `/me` inside `authenticate` block
- AUTH-H2: Replace `requireAdminSession` with `userSession()` + role check
- AUTH-M5: Add absolute session lifetime check on `createdAt`
- INFRA-H2: Fix CSRF null-token constant-time comparison

### Stream 2C: Authorization Gaps

**Scope:** `ConnectionsService.kt`, `ProjectsService.kt`, `ReleasesService.kt`, `ScheduleService.kt`, `TeamAccessService.kt`
**Issues:**
- REL-M4: Add role checks for destructive release operations
- TEAM-H1: Enforce last-lead invariant even for ADMINs
- REL-H2: Add session parameter to `getRelease`/`getBlockExecutions`

### Stream 2D: Credential Exposure

**Scope:** `ConnectionsService.kt`, `NotificationService.kt`, `WebhookRoutes.kt`, `ConnectionTester.kt`
**Issues:**
- NOTIF-H4: Mask webhook URLs in API responses
- HOOK-M2: Mask bearer token in log output
- CONN-M1: Sanitize exception messages in API responses (generic error categories)
- CONN-H4: Add audit log for `updateConnection`

---

## Phase 3 — Data Integrity & Validation

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

## Phase 4 — Resource Management & DoS Prevention

Rate limiting, connection caps, and resource bounds. All streams independent.

### Stream 4A: Rate Limiting & Caps

**Scope:** `Application.kt`, route files, service files
**Issues:**
- REL-H3: Per-user WebSocket connection limit (50)
- REL-H4: Bounded WS channel capacity (1024, DROP_OLDEST)
- HOOK-M1: Rate limit on `/webhooks/status`
- MAVEN-M1: Rate limit on trigger webhook endpoint
- TEAM-M7: Rate limit on invite/join-request creation
- SCHED-H4: Per-project schedule count cap
- MAVEN-M2: Per-project trigger count cap
- NOTIF-M2: Per-project notification config limit

### Stream 4B: Execution Engine Resource Bounds

**Scope:** `ExecutionEngine.kt`, `BuildPollingService.kt`, `TeamCityArtifactService.kt`
**Issues:**
- EXEC-H7: Concurrency semaphore for parallel blocks (50)
- EXEC-M1: Global max poll duration (4 hours)
- EXEC-M2: Re-throw CancellationException in artifact fetcher
- MAVEN-M3: `withTimeoutOrNull` around `pollAllTriggers()`
- MAVEN-M5: Tighter timeout for Maven fetch during create (5s)

### Stream 4C: HTTP Client & TLS

**Scope:** `AppModule.kt`, `MavenMetadataFetcher.kt`
**Issues:**
- EXEC-H6: Configure TLS verification for CIO HttpClient
- MAVEN-H2: Cap Maven metadata response body at 512 KB
- INFRA-M1: HikariCP keepaliveTime, idleTimeout, connectionTestQuery

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
Phase 1 (Critical)     — 7 parallel streams  — All Critical issues
Phase 2 (Security)     — 4 parallel streams  — Auth, SSRF, AuthZ, Credentials
Phase 3 (Validation)   — 3 parallel streams  — Input, Transactions, Schema
Phase 4 (Resources)    — 3 parallel streams  — Rate limits, Bounds, HTTP/TLS
Phase 5 (Correctness)  — 4 parallel streams  — Audit, WebSocket, Webhooks, Executors
Phase 6 (Polish)       — 7 parallel streams  — All remaining Medium + Low
```

Each phase's streams are fully independent and can be worked concurrently.
Phases should be executed in order (1 before 2, etc.) since later phases may depend on
infrastructure changes from earlier phases.
