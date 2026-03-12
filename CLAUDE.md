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
| Database | Exposed (JDBC, not R2DBC) | 0.61.0 |
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
- `DagGraph` (blocks + edges), `ProjectTemplate`, `Release`, `Connection`
- Typesafe IDs: `ProjectId`, `ReleaseId`, `BlockId`, `ConnectionId` (value classes)
- Shared `AppJson` instance in `JsonConfig.kt` — reuse everywhere, do not create separate `Json` configs

## Server Conventions

- **Config**: `loadConfig()` in `Config.kt` reads env vars with defaults. No `@Serializable` on config classes.
- **Database**: Use `newSuspendedTransaction(Dispatchers.IO, db)` for all Exposed queries — never block coroutine threads with bare `transaction {}`.
- **Repositories**: Accept `Database` via constructor injection. Wire via Koin: `single<Repo> { ExposedRepo(get()) }`.
- **Routes**: Validate UUID path parameters before passing to service. Use `ApiRoutes` constants from shared module.
- **Tests**: Use `testApplication` + real Koin modules + H2 in PostgreSQL mode. Override `Config` via Koin module override. Use unique DB URLs per test (`System.nanoTime()` in JDBC URL).

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

After completing each phase:
1. Run a review agent on all changes
2. Fix critical/important/minor issues
3. Commit changes
4. Update memory, CLAUDE.md, and relevant skills with learnings

This keeps multi-session work coherent without relying on conversation context.
