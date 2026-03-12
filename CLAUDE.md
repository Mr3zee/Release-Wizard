# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Release Wizard

A Kotlin library release pipeline builder. Users construct release pipelines from blocks (DAG nodes) connected sequentially, in parallel, or nested in containers. A configured pipeline is a "project template"; running one creates a "release."

## Architecture

Three Gradle modules, all under package `com.github.mr3zee`:

- **`shared/`** — Kotlin Multiplatform library (JVM, JS, WasmJS). Domain models, constants, and shared logic live here. Platform-specific code uses `expect`/`actual` in `jvmMain`, `jsMain`, `wasmJsMain`.
- **`composeApp/`** — Compose Multiplatform client (Desktop JVM + Web via JS/WasmJS). All Compose UI must be in `commonMain` only. Desktop entry: `main.kt` (jvmMain), Web entry: `main.kt` (webMain).
- **`server/`** — Ktor backend (Netty, JVM-only). Entry: `Application.kt`. Auth belongs at the Ktor level, not RPC level.

Dependencies flow: `composeApp` → `shared`, `server` → `shared`. No dependency between `composeApp` and `server`.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Server | Ktor 3.3.3 |
| Database | Exposed with R2DBC |
| UI | Compose Multiplatform 1.10.0 (Desktop + Web focus, keep iOS viable) |
| Language | Kotlin 2.3.0 |
| Build | Gradle with version catalog (`gradle/libs.versions.toml`) |

## Build & Run Commands

```bash
# Desktop app
./gradlew :composeApp:run

# Server
./gradlew :server:run

# Web (Wasm — preferred for modern browsers)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web (JS — legacy browser support)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Build all
./gradlew build

# Run tests
./gradlew test                          # all modules
./gradlew :shared:test                  # shared only
./gradlew :server:test                  # server only
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

This keeps multi-session work coherent without relying on conversation context.
