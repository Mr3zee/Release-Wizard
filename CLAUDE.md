# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Release Wizard

A Kotlin library release pipeline builder. Users construct release pipelines from blocks (DAG nodes) connected sequentially, in parallel, or nested in containers. A configured pipeline is a "project template"; running one creates a "release."

## Architecture

Three Gradle modules, all under package `com.github.mr3zee`:

- **`shared/`** — Kotlin Multiplatform library (JVM, JS, WasmJS). Domain models, constants, shared logic, and `AppJson` configuration live here.
- **`composeApp/`** — Compose Multiplatform client (Desktop JVM + Web via JS/WasmJS). All Compose UI must be in `commonMain` only.
- **`server/`** — Ktor backend (Netty, JVM-only). Entry: `Application.kt`.

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

## Build & Run Commands

```bash
./gradlew :composeApp:run               # Desktop app
./gradlew :server:run                    # Server (requires PostgreSQL)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun  # Web (Wasm)
./gradlew :shared:jvmTest               # Shared tests
./gradlew :server:test                   # Server integration tests (H2)
./gradlew :composeApp:jvmTest            # Compose UI tests (mock HTTP)
```

## Domain Model

Blocks form a DAG. Each node is either a **Container** (holds a sub-graph) or an **Action block** (Slack Message, TeamCity Build, Maven Central Publication, GitHub Action, GitHub Publication, User Action). All domain models live in `shared/src/commonMain/.../model/`

## Key Constraints

- **NEVER use `!!` (non-null assertion) in Kotlin.** Handle nulls gracefully (e.g., `?.`, `?:`, `let`, `when`) or throw an explicit exception with a descriptive message (e.g., `?: error("Expected non-null X because Y")`). No exceptions to this rule.
- Compose code only in `commonMain` source sets
- Add new dependencies through `gradle/libs.versions.toml`, never hardcode versions
- Never use `delay()` in tests — use `waitUntil` or `awaitExecution` patterns
- WebSocket SharedFlow: always subscribe before querying snapshot to prevent race conditions
- `useUnmergedTree = true` when asserting testTags inside Card/Surface/merged containers
- Server conventions (Exposed 1.x packages, Koin wiring, WebSocket testing) → see **ktor-microservice** skill
- Compose UI patterns, canvas testing, and gotchas → see **jetpack-compose-expert** skill
- Manual UI verification → see **compose-ui-test-server** skill

## Development Cycle

To make any changes, follow this workflow in order:
1. **Implement** — build the phase
2. **Review** — run a review agent on all changes
3. **Fix** — address issues from the review
4. **Review again** — re-run review on the fixes
5. **Fix again** — if needed (max 2 review/fix rounds total, then move on)
6. **Manual UI verification** — if the phase included UI changes, use compose-ui-test-server skill for fast visual iteration. Iterate until correct.
7. **Write UI tests** — once behavior is confirmed, write automated `runComposeUiTest` tests in `composeApp/src/jvmTest/` (see jetpack-compose-expert skill).
8. **Update findings** — update memory, CLAUDE.md, and relevant skills with learnings
9. **Review findings** — review the documentation updates for accuracy
10. **Commit** — commit all changes

## Updating Knowledge

When learning something new about the project (gotchas, conventions, patterns):

- **Project-specific conventions** that affect how code is written → update the relevant **skill** (ktor-microservice for server, jetpack-compose-expert for UI, compose-ui-test-server for manual testing). Skills contain the detailed how-to.
- **Cross-cutting constraints** (version requirements, architecture rules, build commands) → update **CLAUDE.md**. Keep it concise — one-liners with pointers to skills for details.
- **Ephemeral project state** (current phase, what's done, what's next) → update **memory** files. These help across sessions but shouldn't duplicate code-derivable facts.
- **Never duplicate** — if something is already in a skill, CLAUDE.md should just reference the skill, not repeat the content. If something is derivable from code or git history, don't store it at all.
