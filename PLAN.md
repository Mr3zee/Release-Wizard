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

*(Not yet started)*

## Phase 3: Connections & Authentication

*(Not yet started)*

## Phase 4: DAG Execution Engine

*(Not yet started)*

## Phase 5: Real-Time Updates via WebSockets

*(Not yet started)*

## Phase 6: External Service Integrations

*(Not yet started)*

## Phase 7: Polish & Production Readiness

*(Not yet started)*
