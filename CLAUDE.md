# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Release Wizard

A Kotlin library release pipeline builder. Users construct release pipelines from blocks (DAG nodes) connected sequentially, in parallel, or nested in containers. A configured pipeline is a "project template"; running one creates a "release."

## Architecture

Three Gradle modules, all under package `com.github.mr3zee`:

- **`shared/`** — Kotlin Multiplatform library (JVM, JS, WasmJS). Domain models, constants, shared logic, and `AppJson` configuration live here. Platform-specific code uses `expect`/`actual` in `jvmMain`, `jsMain`, `wasmJsMain`.
- **`composeApp/`** — Compose Multiplatform client (Desktop JVM + Web via JS/WasmJS). All Compose UI must be in `commonMain` only. Desktop entry: `main.kt` (jvmMain), Web entry: `main.kt` (webMain).
- **`server/`** — Ktor backend (Netty, JVM-only). Entry: `Application.kt`. Auth belongs at the Ktor level, not RPC level.

Dependencies flow: `composeApp` → `shared`, `server` → `shared`. No dependency between `composeApp` and `server`.

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Server | Ktor | 3.3.3 |
| Database | Exposed (JDBC) | 1.1.1 |
| DI | Koin | 4.1.1 |
| UI | Compose Multiplatform | 1.10.0 |
| Language | Kotlin | 2.3.0 |
| Build | Gradle with version catalog | `gradle/libs.versions.toml` |

**Important**: Koin 4.1.1+ is required for Ktor 3.x compatibility. Koin 4.0.x targets Ktor 2.x and will fail at runtime.

## Build & Run Commands

```bash
# Desktop app
./gradlew :composeApp:run

# Server (requires PostgreSQL)
./gradlew :server:run

# Web (Wasm — preferred for modern browsers)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web (JS — legacy browser support)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Build all
./gradlew build

# Run tests
./gradlew :shared:jvmTest              # shared serialization tests
./gradlew :server:test                  # server integration tests (H2)
./gradlew :composeApp:jvmTest           # compose app (JVM)
```

## Block System (Core Domain)

Blocks form a DAG. Each node is either:
- **Container** — holds a sub-graph; no action of its own; blocks inside cannot connect outside
- **Action block** — one of: Slack Message, TeamCity Build, Maven Central Portal Publication, GitHub Action, GitHub Publication, User Action

Action block properties:
- Status: waiting | running | failed | succeeded | waiting_for_input
- Parameters (can inherit from project or be block-specific)
- Outputs (defined per block type, usable as inputs for downstream blocks)
- Optional timeout
- Can be restarted/paused/stopped at any time

Connection types between blocks: sequential (waits for all predecessors), parallel, container (scoped sub-graph).

## Connections (External Service Integrations)

Reusable across projects. Types: Slack, TeamCity, GitHub (token), Maven Central Portal. Credentials must be stored securely.

## Shared Domain Models

All domain models live in `shared/src/commonMain/.../model/` and use `@Serializable`. Key types:
- `Block` (sealed: `ContainerBlock`, `ActionBlock`) — uses `@JsonClassDiscriminator("kind")` to avoid conflict with `type` property
- `DagGraph` (blocks + edges + positions), `BlockPosition` (x, y in dp), `ProjectTemplate`, `Release`, `Connection`
- Typesafe IDs: `ProjectId`, `ReleaseId`, `BlockId`, `ConnectionId` (value classes)
- Shared `AppJson` instance in `JsonConfig.kt` — reuse everywhere, do not create separate `Json` configs
- DAG validation in `shared/.../dag/`: `DagValidator` (cycles, self-loops, duplicate IDs, invalid edges) and `DagTopologicalSort` (Kahn's algorithm)

## Server Conventions

- **Config**: Server uses `application.yaml` + `EngineMain`. Ktor handles host/port from `ktor.deployment.*`. Custom config (database) is read via `environment.config.databaseConfig()` in `Application.module()` and passed to `appModule(dbConfig)`. No `loadConfig()` function — all config comes from the YAML file.
- **Database**: Use `newSuspendedTransaction(Dispatchers.IO, db)` for all Exposed queries — never block coroutine threads with bare `transaction {}`.
- **Repositories**: Accept `Database` via constructor injection. Wire via Koin: `single<Repo> { ExposedRepo(get()) }`.
- **Routes**: Validate UUID path parameters before passing to service. Use `ApiRoutes` constants from shared module.
- **Tests**: Use `testApplication` + real Koin modules + H2 in PostgreSQL mode. Pass `DatabaseConfig` directly to `appModule(testDbConfig())` — no YAML or Koin overrides needed. Use unique DB URLs per test (`System.nanoTime()` in JDBC URL).

## Canvas Editor Conventions

The DAG editor (`composeApp/.../editor/`) uses a pure Canvas approach for rendering blocks, edges, and ports:
- **Coordinate system**: Block positions stored as dp values in `DagGraph.positions`. Canvas converts to screen pixels via `CanvasTransform(zoom, panOffset, density)`.
- **Pointer handling**: All interactions (select, drag, pan, zoom, connect) handled in `pointerInput` on the Canvas. Never use `remember`ed derived values inside `pointerInput` lambdas — create `CanvasTransform` inline from state reads to avoid stale captures.
- **Undo/redo**: Structural changes (add/remove block/edge, move) push to undo stack. Property changes (name, params, timeout) use `updateGraphSilent` to avoid flooding undo with per-keystroke entries. Type changes are discrete and do push undo.
- **Color mapping**: `blockTypeColor()` in `DagCanvas.kt` is the single source of truth for block type colors, shared with `EditorToolbar.kt`.
- **Keyboard shortcuts**: Use `isCtrlPressed || isMetaPressed` for cross-platform Ctrl/Cmd support.

## Key Constraints

- Compose code only in `commonMain` source sets
- UI should feel native on macOS/iOS, even in Web
- Auth at Ktor level
- Credentials stored securely
- Add new dependencies through `gradle/libs.versions.toml`, never hardcode versions

## Development Plan Tracking

Implementation plans are maintained as `PLAN.md` at the project root. When starting a significant feature:
1. Write/update `PLAN.md` with the approach, phases, and current progress
2. Check off completed items as work progresses
3. Remove the plan when the feature is fully implemented

After completing each phase, follow this workflow in order:
1. **Implement** — build the phase
2. **Review** — run a review agent on all changes
3. **Fix** — address issues from the review
4. **Review again** — re-run review on the fixes
5. **Fix again** — if needed (max 2 review/fix rounds total, then move on)
6. **UI verification** — if the phase included UI changes, run compose-ui-test-server to verify visually (see below)
7. **Update findings** — update memory, CLAUDE.md, and relevant skills with learnings
8. **Review findings** — review the documentation updates for accuracy
9. **Commit** — commit all changes
10. **Complete** — phase is done, move to next

This keeps multi-session work coherent without relying on conversation context.

## UI Verification (compose-ui-test-server)

After any phase with UI changes, verify the UI with compose-ui-test-server. The library is installed in `composeApp/jvmMain` and `main.kt` uses `runApplication()` from the library.

```bash
# Launch with test server enabled (requires server running for API calls)
COMPOSE_UI_TEST_SERVER_ENABLED=true ./gradlew :composeApp:run

# Health check
curl http://localhost:54345/health

# Interact with UI elements by test tag
curl http://localhost:54345/onNodeWithTag/{tag}/performClick
curl "http://localhost:54345/onNodeWithTag/{tag}/performTextInput?text=..."
curl "http://localhost:54345/waitUntilExactlyOneExists/tag/{tag}?timeout=5000"
curl http://localhost:54345/waitForIdle

# Capture screenshot for visual verification
curl "http://localhost:54345/captureScreenshot?path=/tmp/screenshot.png"
```

Always `waitForIdle` between actions. URL-encode special characters in text inputs.
