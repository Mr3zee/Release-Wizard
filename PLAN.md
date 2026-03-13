# Release Wizard — Implementation Plan

## Context

Release Wizard is a Kotlin library release pipeline builder. Users construct pipelines from blocks (DAG nodes) connected sequentially/in parallel/nested in containers. A configured pipeline is a "project template"; running one creates a "release." The primary use case is Kotlin library releases (build → test → publish Maven Central → GitHub release → notify).

### Architectural Decisions
- **API**: Ktor REST + WebSockets (real-time pipeline status)
- **Database**: PostgreSQL + Exposed
- **Auth**: Session-based (Ktor level)
- **User model**: Single-user now, design for multi-user later
- **DAG storage**: JSON blob in DB + denormalized index tables
- **Execution**: In-process coroutine engine
- **UI**: Visual DAG editor (canvas-based, Compose Multiplatform)
- **Release trigger**: Manual only
- **Parameters**: Project-level params + block output→input wiring
- **Integrations priority**: TeamCity → GitHub → Slack → Maven Central Portal
- **User Action blocks**: Approval gate OR manual task + can request user input (multiline, template interpolation)

---

## Phase 0: Project Setup

- [x] Write this plan to `PLAN.md` at project root
- [x] Check off items in PLAN.md as work progresses

---

## Phase 1: Foundation — Domain Models, Build Config, Project CRUD

**Goal**: Shared domain models with serialization, PostgreSQL + Exposed, Koin DI, REST API for project templates, basic list UI in Compose.

### 1a. Dependencies (`gradle/libs.versions.toml`)

- [x] Add: `kotlinx-serialization-json`, `kotlinx-datetime`, `exposed-core`, `exposed-jdbc`, `exposed-json`, `postgresql`, `hikaricp`, `koin-ktor`, `koin-logger-slf4j`, `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-server-status-pages`, `ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation`, `h2` (for tests)
- [x] Update: `shared/build.gradle.kts` (serialization plugin + deps), `server/build.gradle.kts` (Exposed, PG, Koin, Ktor plugins), `composeApp/build.gradle.kts` (Ktor client, serialization)

### 1b. Shared Domain Models (`shared/src/commonMain/kotlin/com/github/mr3zee/`)

- [x] model/Id.kt — typesafe ID wrappers (ProjectId, ReleaseId, BlockId, ConnectionId)
- [x] model/BlockType.kt — enum
- [x] model/BlockStatus.kt — enum
- [x] model/ConnectionType.kt — enum
- [x] model/Parameter.kt — key/value with template interpolation support
- [x] model/Block.kt — sealed: ContainerBlock, ActionBlock
- [x] model/Edge.kt — fromBlockId -> toBlockId
- [x] model/DagGraph.kt — blocks + edges
- [x] model/ProjectTemplate.kt
- [x] model/Release.kt + ReleaseStatus.kt
- [x] model/Connection.kt + ConnectionConfig sealed class
- [x] api/ApiRoutes.kt — string constants for all API paths
- [x] api/ProjectDtos.kt — CreateProjectRequest, UpdateProjectRequest, ProjectResponse
- [x] Remove Greeting.kt

### 1c. Server: Database + Koin + Project CRUD

- [x] Application.kt — install Koin, ContentNegotiation, StatusPages, configureRouting()
- [x] AppModule.kt — Koin: config, DataSource, schema init
- [x] Config.kt — database url/user/pass, server host/port (env vars with defaults)
- [x] persistence/DatabaseFactory.kt — HikariCP DataSource, schema init
- [x] persistence/ProjectTemplateTables.kt — Exposed table with JSONB columns
- [x] projects/ProjectsModule.kt — Koin module
- [x] projects/ProjectsRoutes.kt — GET/POST/PUT/DELETE /api/v1/projects
- [x] projects/ProjectsService.kt — interface + DefaultProjectsService
- [x] projects/ProjectsRepository.kt — interface
- [x] projects/ExposedProjectsRepository.kt — Exposed impl

### 1d. Client: API Client + Project List

- [x] App.kt — navigation shell, MaterialTheme
- [x] navigation/Screen.kt — sealed class
- [x] navigation/AppNavigation.kt — router
- [x] api/HttpClientFactory.kt — Ktor HttpClient with JSON
- [x] api/ProjectApiClient.kt — suspend functions for project CRUD
- [x] projects/ProjectListScreen.kt — LazyColumn of projects, FAB to create
- [x] projects/ProjectListViewModel.kt — StateFlow<List<ProjectTemplate>>

### 1e. Tests & Verification

- [x] shared: serialization round-trip tests (9 tests)
- [x] server: integration tests with testApplication + H2 (7 tests)
- [x] ./gradlew build passes

---

## Phase 2: Visual DAG Editor

**Goal**: Canvas-based drag-and-drop editor where users add blocks, connect them, and save as project templates.

### 2a. Layout Model

- [x] model/BlockPosition.kt — x, y coordinates (dp)
- [x] Update DagGraph to include `positions: Map<BlockId, BlockPosition>`

### 2b. DAG Validation (`shared/src/commonMain/`)

- [x] dag/DagValidator.kt — no cycles, duplicate IDs, self-loops, invalid edges, recursive container validation
- [x] dag/DagTopologicalSort.kt — Kahn's algorithm

### 2c. Canvas Components (`composeApp/src/commonMain/`)

- [x] editor/DagCanvas.kt — Canvas rendering (blocks, edges, ports, grid) + pointer handling (select, drag, pan, zoom, connect)
- [x] editor/EditorToolbar.kt — Block palette (all block types + container), undo/redo, delete
- [x] editor/BlockPropertiesPanel.kt — Edit name, type, params, timeout for selected block
- [x] editor/DagEditorScreen.kt — Scaffold with toolbar, canvas, properties, keyboard shortcuts (Delete, Ctrl+Z/Y, Ctrl+S)
- [x] editor/DagEditorViewModel.kt — State management, undo/redo stack, API save, validation

### 2d. Integration

- [x] api/ProjectApiClient.kt — add updateProject method
- [x] AppNavigation.kt — route to DagEditorScreen
- [x] App.kt — pass API client to navigation

### 2e. Tests & Verification

- [x] DagValidatorTest — 8 tests (empty, linear, diamond, self-loop, cycle, duplicate ID, invalid edge, container children)
- [x] DagTopologicalSortTest — 7 tests (empty, single, linear, diamond, cycle, disconnected, partial cycle)
- [x] Updated serialization test for DagGraph with positions
- [x] All 25 shared tests pass, all 10 server tests pass, all targets compile (JVM, JS, WasmJS)

### 2f. UI Verification (compose-ui-test-server)

- [x] Install compose-ui-test-server in composeApp
- [x] Launch app with test server, verify project list, create project, navigate to editor
- [x] Verify editor toolbar, canvas, properties panel render correctly via screenshots

---

## UI Development & Testing Process

For phases with UI changes, follow this two-step process:

### Step 1: Manual verification (fast iteration)

Use `compose-ui-test-server` to visually verify during development. Iterate on layout and behavior until correct.

1. **Start the backend**: `./gradlew :server:run`
2. **Start the app with test server**: `COMPOSE_UI_TEST_SERVER_ENABLED=true ./gradlew :composeApp:run &`
3. **Health check**: `curl http://localhost:54345/health`
4. **Interact**: click buttons, enter text, wait for elements via HTTP endpoints
5. **Screenshot**: `curl "http://localhost:54345/captureScreenshot?path=/tmp/screenshot.png"`
6. **Verify**: read screenshots to validate UI layout and state
7. **Iterate**: fix issues, re-run, re-screenshot until correct

### Step 2: Automated UI tests (lock in behavior)

Once manual verification confirms the UI is correct, write `runComposeUiTest` tests in `composeApp/src/jvmTest/` to lock in the behavior as regression tests. These use `MockHttpClient` (Ktor `MockEngine`) — no server needed.

For each new/changed screen, test:
- **Happy path**: data loads and renders correctly
- **Empty state**: no data shows appropriate message
- **Error state**: API failure shows error UI with retry
- **Interactions**: button callbacks, form validation, navigation

Run: `./gradlew :composeApp:jvmTest`

Key test tags available:
- Project list: `project_list_screen`, `create_project_fab`, `project_name_input`, `project_list`, `project_item_{id}`, `connections_button`, `logout_button`
- Editor: `dag_editor_screen`, `dag_canvas`, `save_button`, `add_block_{TYPE}`, `add_container`, `undo_button`, `redo_button`, `delete_button`
- Properties: `block_name_field`, `block_type_selector`, `block_timeout_field`, `add_parameter_button`
- Login: `login_screen`, `login_username`, `login_password`, `login_button`
- Connections: `connection_list_screen`, `create_connection_fab`, `connection_list`, `connection_item_{id}`, `connection_form_screen`, `connection_name_field`, `connection_type_selector`, `save_connection_button`

---

## Phase 3: Connections & Authentication

**Goal**: Session-based auth + connection CRUD with encrypted credential storage.

### 3a. Dependencies

- [x] Add `ktor-server-auth`, `ktor-server-sessions` to `libs.versions.toml` and `server/build.gradle.kts`

### 3b. Shared: DTOs & API Routes

- [x] `api/AuthDtos.kt` — LoginRequest, UserInfo
- [x] `api/ConnectionDtos.kt` — Create/Update/Response/List/TestResult DTOs
- [x] Update `ApiRoutes.kt` — Auth routes (login, logout, me), Connection test route
- [x] Update `Connection.kt` — add `updatedAt` field

### 3c. Server: Authentication

- [x] `Config.kt` — AuthConfig, EncryptionConfig + config reader extensions
- [x] `auth/UserSession.kt` — @Serializable session data class implementing Principal
- [x] `auth/AuthService.kt` — interface + ConfigAuthService (validates against config)
- [x] `auth/AuthModule.kt` — Koin module
- [x] `auth/AuthRoutes.kt` — POST /login, POST /logout, GET /me
- [x] `Application.kt` — install Sessions (cookie with HMAC signing), Authentication (session provider)
- [x] `Application.kt` — wrap `/api/v1/*` routes in `authenticate("session-auth")`
- [x] `application.yaml` — auth config (username, password, sessionSignKey)

### 3d. Server: Connections

- [x] `security/EncryptionService.kt` — AES-256-GCM encrypt/decrypt
- [x] `persistence/ConnectionTable.kt` — Exposed table with encrypted_config text column
- [x] `connections/ConnectionsRepository.kt` — interface
- [x] `connections/ExposedConnectionsRepository.kt` — encrypts config on write, decrypts on read
- [x] `connections/ConnectionsService.kt` — interface + DefaultConnectionsService with credential masking
- [x] `connections/ConnectionsRoutes.kt` — CRUD + POST /:id/test (stub)
- [x] `connections/ConnectionsModule.kt` — Koin module
- [x] `AppModule.kt` — updated to accept AuthConfig + EncryptionConfig, provides EncryptionService
- [x] `DatabaseFactory.kt` — create ConnectionTable
- [x] `application.yaml` — encryption key config

### 3e. Client

- [x] `api/AuthApiClient.kt` — login, logout, me
- [x] `api/ConnectionApiClient.kt` — CRUD + test
- [x] `api/HttpClientFactory.kt` — install HttpCookies for session persistence
- [x] `auth/AuthViewModel.kt` — auth state management (checkSession, login, logout)
- [x] `auth/LoginScreen.kt` — login form with username/password
- [x] `connections/ConnectionsViewModel.kt` — connections CRUD + test
- [x] `connections/ConnectionListScreen.kt` — list with test/delete actions
- [x] `connections/ConnectionFormScreen.kt` — create form with type-specific fields
- [x] `navigation/Screen.kt` — add ConnectionList, ConnectionForm screens
- [x] `navigation/AppNavigation.kt` — route new screens, pass onLogout
- [x] `App.kt` — auth gate (LoginScreen when not authenticated), connections wiring
- [x] `projects/ProjectListScreen.kt` — add Connections + Logout buttons to TopAppBar

### 3f. Tests & Verification

- [x] `auth/AuthRoutesTest.kt` — 7 tests (login, invalid login, me, me unauthorized, logout, protected endpoint requires auth, protected endpoint after login)
- [x] `connections/ConnectionsRoutesTest.kt` — 8 tests (list, create+get, blank name, update, delete, test endpoint, unauthenticated, encrypted credentials masked)
- [x] Updated `ProjectsRoutesTest.kt` — all tests login first + unauthenticated test
- [x] All test clients install `HttpCookies` for session persistence
- [x] Shared serialization tests — Connection round-trip, ConnectionDtos round-trip, AuthDtos round-trip
- [x] All 26 server tests pass, all 28 shared tests pass, all targets compile (JVM, JS, WasmJS)

### 3g. UI Verification (compose-ui-test-server)

- [x] Launch app with test server, verify login screen
- [x] Login, verify project list with Connections/Logout buttons
- [x] Navigate to connections, verify empty state
- [x] Create connection, verify it appears in list

## Phase 4: DAG Execution Engine

**Goal**: Coroutine-based engine that traverses the DAG, executes blocks, manages lifecycle.

### 4a. Shared: Models & DTOs

- [x] `model/BlockExecution.kt` — block-level execution state (blockId, releaseId, status, outputs, error, timestamps)
- [x] `api/ReleaseDtos.kt` — CreateReleaseRequest, ReleaseResponse, ReleaseListResponse, ApproveBlockRequest
- [x] Update `ApiRoutes.kt` — cancel, restartBlock, approveBlock, blockExecution routes
- [x] `template/TemplateEngine.kt` — resolves `${param.key}` and `${block.blockId.outputName}` in parameter values

### 4b. Server: Persistence

- [x] `persistence/ReleaseTables.kt` — ReleaseTable (projectTemplateId, status, dagSnapshot jsonb, parameters jsonb, timestamps) + BlockExecutionTable (releaseId, blockId, status, outputs jsonb, error, timestamps)
- [x] `persistence/DatabaseFactory.kt` — add ReleaseTable, BlockExecutionTable to SchemaUtils.create()

### 4c. Server: Releases CRUD

- [x] `releases/ReleasesRepository.kt` — interface (CRUD + block execution upsert/query)
- [x] `releases/ExposedReleasesRepository.kt` — Exposed implementation with varchar FK columns
- [x] `releases/ReleasesService.kt` — interface + DefaultReleasesService (start, cancel, restart block, approve block, param merging)
- [x] `releases/ReleasesRoutes.kt` — GET/POST releases, POST /:id/cancel, POST /:id/blocks/:blockId/restart, POST /:id/blocks/:blockId/approve
- [x] `releases/ReleasesModule.kt` — Koin module (repository, service, block executor, execution engine)

### 4d. Server: Execution Engine

- [x] `execution/BlockExecutor.kt` — interface for executing a single action block
- [x] `execution/ExecutionContext.kt` — release state, params, block outputs, connections
- [x] `execution/StubBlockExecutor.kt` — returns simulated outputs per block type (real executors in Phase 6)
- [x] `execution/ExecutionEngine.kt` — coroutine-based DAG execution:
  - Topological sort → wave-based parallel execution
  - Predecessor tracking: blocks launch when all predecessors SUCCEEDED
  - Container blocks: recursive sub-DAG execution
  - User Action blocks: suspend on CompletableDeferred, resume on approve endpoint
  - Timeout support via `withTimeout`
  - Template parameter resolution via TemplateEngine
  - Cancellation via structured concurrency
  - Block status persisted to DB immediately on state changes

### 4e. Wiring

- [x] `Application.kt` — add releasesModule + releaseRoutes inside authenticate block
- [x] `TestUtils.kt` — add releasesModule to test Koin modules

### 4f. Tests & Verification

- [x] `releases/ReleasesRoutesTest.kt` — 12 tests (list, start+get, nonexistent project, empty DAG, sequential DAG execution, diamond DAG execution, user action approval, cancel, nonexistent release, unauthenticated, parameter merging, block outputs)
- [x] `template/TemplateEngineTest.kt` — 7 tests (param resolution, block output, unresolved kept, multiple expressions, resolveParameters, no-template unchanged, multi-part block output)
- [x] Updated `SerializationTest.kt` — 4 new tests (BlockExecution, BlockExecution with error, ReleaseDtos, ReleaseResponse)
- [x] All 39 server tests pass, all 39 shared tests pass (78 total), all targets compile (JVM, JS, WasmJS)

## Phase 5: Real-Time Updates via WebSockets

**Goal**: Stream release execution updates to the client via WebSockets.

- [x] `api/WebSocketEvents.kt` — ReleaseEvent sealed class (Snapshot, ReleaseStatusChanged, BlockExecutionUpdated, ReleaseCompleted)
- [x] `releases/ReleaseWebSocketRoutes.kt` — WebSocket endpoint at /api/v1/releases/{id}/ws with snapshot + event streaming
- [x] ExecutionEngine SharedFlow event broadcasting
- [x] Client WebSocket subscription and real-time UI updates
- [x] Release management UI (start, cancel, status display)
- [x] `releases/ReleaseWebSocketTest.kt` — 5 tests (snapshot, events, completion, nonexistent, disconnect)
- [x] Compose UI tests — 97 tests covering all screens

---

## Phase 6: External Service Integrations

**Goal**: Replace stubs with real API integrations for all 5 block types + webhook infrastructure.

### 6a. Foundation

- [x] Shared model changes: `webhookSecret` on TeamCityConfig/GitHubConfig, `baseUrl` on MavenCentralConfig
- [x] `webhookUrl` on ConnectionResponse, `webhookUrls` on ConnectionListResponse
- [x] WebhookConfig (`webhookBaseUrl`) in server config + application.yaml
- [x] Ktor HttpClient(CIO) registered in Koin for outbound API calls
- [x] `DispatchingBlockExecutor` — routes to type-specific executors by BlockType
- [x] `PendingWebhookTable` + `PendingWebhook` model + `PendingWebhookRepository`
- [x] `ExecutionEngine` populates `ExecutionContext.connections` from DB via `ConnectionsRepository`
- [x] `WebhooksModule` — Koin module for webhook infrastructure
- [x] UI: webhookSecret fields for TC/GH, baseUrl for Maven Central, webhook URL display in connection list
- [x] `ApiRoutes.Webhooks` — route constants for webhook receiver endpoints

### 6b. Webhook Infrastructure

- [x] `WebhookService` — validates secrets, updates PendingWebhook records, emits SharedFlow completions
- [x] HMAC-SHA256 validation for GitHub webhooks (`X-Hub-Signature-256`), secret validation for TeamCity (`X-Webhook-Secret`)
- [x] Timing-safe comparison via `MessageDigest.isEqual`
- [x] `WebhookRoutes` — POST endpoints outside auth block at `/api/v1/webhooks/{type}/{connectionId}`
- [x] `WebhookRoutesTest` — 12 integration tests (valid/invalid secrets, error cases, auth bypass)

### 6c. Synchronous Executors

- [x] `SlackMessageExecutor` — POST to incoming webhook URL with text/channel
- [x] `GitHubPublicationExecutor` — POST to GitHub releases API with Bearer auth
- [x] `ExecutorsTest` — 9 unit tests with MockEngine
- [x] Test infrastructure: `StubBlockExecutor` override in tests, mock HttpClient

### 6d. Connection Testing

- [x] `ConnectionTester` — real API validation per connection type (TC server, GH repo, Maven Central status, Slack URL format)
- [x] Wired into `ConnectionsService.testConnection()` and routes
- [x] `TestMockClient` — separate file for mock HttpClient to avoid import conflicts

### 6e. Async Executors (Webhook-based)

- [x] `TeamCityBuildExecutor` — triggers build via `/app/rest/buildQueue`, registers PendingWebhook, awaits completion via SharedFlow
- [x] `GitHubActionExecutor` — triggers workflow dispatch, discovers run ID via polling, registers PendingWebhook, awaits completion
- [x] `CoroutineStart.UNDISPATCHED` ensures SharedFlow subscriber is active before webhook registration (prevents race condition)
- [x] XML escaping for TeamCity parameters
- [x] `AsyncExecutorsTest` — 4 tests with in-memory webhook repository, yield-based `waitUntil` polling

### 6f. Maven Central Executor

- [x] `MavenCentralExecutor` — two modes: by deploymentId or by groupId+artifactId+version
- [x] All 5 block types now use real executors (StubBlockExecutor only in tests)

### Review Fixes

- [x] `emitCompletionOnce` with AtomicBoolean prevents duplicate ReleaseCompleted events from cancelExecution + catch block race
- [x] `tryEmit` → `emit` in WebhookService prevents silent event drops
- [x] Default status "UNKNOWN"/"unknown" on parse failure with logging (not false "SUCCESS")
- [x] No credential leaking in connection test error messages
- [x] `yield()`-based `waitUntil` in tests (no `delay()`)

---

## Phase 7: Polish & Production Readiness

- [x] **7a: Server Error Handling & Health Check** — `ErrorResponse` contract in shared module, `CorrelationId` plugin, `StatusPages` overhaul (all errors return JSON), `NotFoundException`, `GET /health`, route cleanup
- [x] **7b: Backend Release Recovery** — `BlockExecutor.resume()` interface, per-executor resume strategies (Slack=FAIL, GitHub Publication=check-by-tag, TeamCity/GitHub Action=check PendingWebhook state, Maven Central=re-execute), `RecoveryService`, `ExecutionEngine.recoverRelease()`, startup hook via `ApplicationStarted`, stale webhook cleanup
- [x] **7c: Config Externalization** — Env var override via `propertyOrEnv()` helper for all sensitive config values (DB_URL, DB_USER, DB_PASSWORD, AUTH_USERNAME, AUTH_PASSWORD, AUTH_SESSION_SIGN_KEY, ENCRYPTION_KEY, WEBHOOK_BASE_URL)
- [x] **7f: Logging Improvements** — logback.xml updated (root=INFO, com.github.mr3zee=DEBUG, correlationId in pattern), `CallLogging` plugin for /api/ paths, structured logging in ExecutionEngine, RecoveryService, WebhookService, AuthService
- [x] **7d: Client Auth Resilience** — `AuthEventBus` singleton with `SessionExpired` event, `HttpClient` 401 interception (excludes login), `AuthViewModel.onSessionExpired()`, App.kt subscription
- [x] **7e: Client Error Polish** — `ErrorResponse` parsing utility, `Exception.toUserMessage()` extension, all ViewModel catch blocks updated, reconnect attempt counter in `ReleaseDetailViewModel`
- [x] **7g: Tests** — Health check (UP without auth), error format tests (400/401/404 return JSON ErrorResponse with correlationId), recovery tests (RUNNING resumes, SUCCEEDED skipped, FAILED skipped, user action re-registered, stale webhook cleanup), client tests (error parsing, auth event bus, login 401 exclusion)
