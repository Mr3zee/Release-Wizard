<p align="center">
  <img src="icons/logo-dark-bg/logo-dark-bg_128x128.png" alt="Release Wizard" />
</p>

<h1 align="center">Release Wizard</h1>

<p align="center">
  A visual release pipeline builder for orchestrating multi-service deployments across TeamCity, GitHub, and Slack.
</p>

---

Release Wizard lets you design release pipelines as visual DAGs (directed acyclic graphs), connect them to your CI/CD infrastructure, and execute them with real-time monitoring. Build a pipeline once as a reusable **project template**, then run it repeatedly to create **releases**.

## Key Features

**Visual Pipeline Editor** — Drag-and-drop canvas for building release DAGs. Blocks connect sequentially, in parallel, or nested inside containers for hierarchical pipelines. Undo/redo, auto-save, and concurrent edit locking included.

**Block Types:**
- **TeamCity Build** — trigger builds, download artifacts, track sub-builds
- **GitHub Action** — dispatch workflows
- **GitHub Publication** — trigger release workflows
- **Slack Message** — post notifications via webhooks
- **Container** — nest an entire sub-pipeline inside a single block

**Live Release Monitoring** — Real-time execution tracking via WebSockets. Watch blocks transition through pending/running/success/failed states, view error logs, and browse artifact trees.

**Automation Triggers** — Schedule releases with cron expressions, expose webhook URLs for external systems, or monitor Maven repositories for new artifact versions to auto-trigger pipelines.

**Team Collaboration** — Role-based access control, team invitations, project ownership by team, and full audit logging of all actions.

**Multi-Platform Client** — Runs as a desktop app (JVM) or in the browser (WebAssembly/JS). Same Compose Multiplatform codebase for both.

## Architecture

Three Gradle modules, all under the `com.github.mr3zee` package:

```
┌─────────────┐     ┌─────────────┐
│  composeApp  │     │   server    │
│  (Desktop +  │     │  (Ktor +    │
│   Web UI)    │     │  PostgreSQL)│
└──────┬───────┘     └──────┬──────┘
       │                    │
       └────────┬───────────┘
                │
         ┌──────┴──────┐
         │   shared    │
         │  (Models +  │
         │   Logic)    │
         └─────────────┘
```

- **shared/** — Kotlin Multiplatform library (JVM, JS, WasmJS). Domain models, DAG validation, serialization, and constants.
- **composeApp/** — Compose Multiplatform client. All UI code lives in `commonMain`. Desktop (JVM) and Web (Wasm/JS) targets.
- **server/** — Ktor backend with PostgreSQL. Handles auth, execution engine, integrations, WebSocket updates, and REST API.

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.3.0 |
| UI | Compose Multiplatform | 1.10.0 |
| Server | Ktor | 3.3.3 |
| Database | PostgreSQL + Exposed ORM | 1.1.1 |
| DI | Koin | 4.1.1 |
| Build | Gradle (Kotlin DSL) | wrapper included |

## Getting Started

### Prerequisites

- JDK 21+
- Docker & Docker Compose

### Local Development

```bash
# Start PostgreSQL
docker compose up -d

# Generate .env with secrets (first time only)
./scripts/setup-local-env.sh

# Run the server (http://localhost:8080)
source .env && ./gradlew :server:run
```

### Desktop App

```bash
./gradlew :composeApp:run
```

### Web App (Wasm)

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

### Split Mode (Frontend Hot-Reload)

Run backend and frontend in separate terminals for faster UI iteration:

```bash
# Terminal 1
source .env && ./gradlew :server:run

# Terminal 2 (http://localhost:8081)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

## Testing

```bash
./gradlew :shared:jvmTest          # Shared unit tests
./gradlew :server:test             # Server tests (H2 in-memory DB)
./gradlew :composeApp:jvmTest      # Compose UI tests (mock HTTP)
./gradlew :e2eTest:test            # End-to-end (embedded server + UI)
./gradlew :server:integrationTest  # Integration tests (real APIs, requires Docker + credentials)
```

## Security

- Argon2 password hashing
- AES-256 encryption for stored credentials
- Encrypted sessions with CSRF protection
- SSRF protection on webhook URLs
- Rate limiting on auth endpoints
- Account lockout after failed login attempts

## Internationalization

Six language packs: English, Russian, Spanish, French, German, and Chinese.

## License

All rights reserved.
