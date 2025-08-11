# Release Wizard — Assessment and Development Plan

Date: 2025-08-11

## Assessment (Current Status)

Overview:
- Monorepo with three modules:
  - shared: RPC service interfaces (annotated with @Rpc) and domain models.
  - server: Ktor application, Exposed R2DBC setup with comprehensive schema; implementations for some services.
  - composeApp: Compose Multiplatform UI (Desktop/Web/iOS) scaffolding.

Findings by area:
- Server runtime
  - Ktor module configures JSON and WebSockets and serves static UI; health endpoint exists.
  - Missing kotlinx.rpc wiring/transport to expose shared @Rpc services over Ktor/WebSockets/HTTP.
- Database
  - Exposed R2DBC (async) is configured properly (DatabaseConfig) with tr helper wrapping suspend transactions.
  - Schema covers users/auth, connections (+ credentials), projects (+ message templates), releases, block executions, execution logs, and user inputs.
- Services
  - ProjectServiceImpl: Implemented CRUD/list/validate/template stubs using Exposed R2DBC.
  - AuthServiceImpl: Implemented login/refresh/logout/token validation with BCrypt and sessions.
  - ReleaseServiceImpl: Largely not implemented (returns "Not implemented" for most methods).
  - GitHubConnectionServiceImpl: Only create is partially implemented; the rest are TODO. Similar state expected for Slack/TeamCity/Maven connection services (to be confirmed while implementing).
- Integrations
  - GitHubClient: Rich Ktor-based client for user/repos/branches/releases/assets; not wired into services.
  - Other integrations (TeamCityClient, SlackClient, MavenCentralClient) exist but need service wiring and tests.
- UI
  - App scaffolds navigation across many screens, but screens are placeholders.
  - ProjectListScreen uses mocked state; no data fetching from backend.
  - No DAG editor for block graph yet; Release monitor is placeholder; Connections screens are placeholders.
- Testing & Tooling
  - No tests found. No visible RPC or UI integration tests yet.
  - No CI configuration observed in repo root.

Key gaps and risks:
- RPC communication layer is absent; without it the UI can’t call server services.
- Release execution engine is unimplemented; only domain and schema exist.
- Security/Secrets management for connection credentials is not implemented (encryption, storage, rotation).
- No tests; risk of regressions and integration breakage.
- UI is early-stage; lacks data wiring and editing capabilities (especially DAG/block editor).

Constraints/Assumptions:
- Continue using Exposed R2DBC (>= 1.0.0-beta-5) and kotlinx.rpc for comms.
- Compose UI in commonMain only; Desktop/Web first-class.
- Ask before adding or upgrading external libraries.

---

## Stage 1: RPC Wiring and Minimal E2E Flow
**Goal**: Expose server services via kotlinx.rpc and connect the UI to list projects from the database.
**Success Criteria**:
- kotlinx.rpc server installed and routes registered for at least ProjectService and AuthService.
- UI loads and displays projects from server (no mocks) with loading/empty states.
- Health check + a basic auth token flow validated (can be stubbed or optional for first list call).
**Tests**:
- Server unit test: ProjectServiceImpl.listProjects returns empty list against an in-memory/test DB.
- Integration test: RPC call to listProjects returns 200 and decodes response.
- UI instrumentation/unit test: ProjectListScreen renders items from a fake ProjectService client.
**Status**: Not Started

## Stage 2: Connections CRUD and Secrets Storage
**Goal**: Implement CRUD for connections (GitHub, Slack, TeamCity, Maven Central), storing encrypted credentials.
**Success Criteria**:
- ConnectionService implementations complete for create/update/delete/get/list.
- Credentials are encrypted before storage and can be decrypted for use.
- Basic "Test connection" implemented for GitHub using GitHubClient.testConnection.
- UI connection list/editor can create and list connections via RPC.
**Tests**:
- Unit tests for credential serialization/encryption/decryption roundtrip.
- Service tests for create/update/delete/list and error conditions.
- Integration test for GitHub testConnection using a mock HTTP client.
**Status**: Not Started

## Stage 3: Release Pipeline MVP
**Goal**: Create and start a release from a project’s block graph with at least two block types functioning (Slack message, User action).
**Success Criteria**:
- ReleaseService implements createRelease, getRelease, listReleases, startRelease for MVP.
- Block execution state machine handles waiting/running/succeeded/failed for basic blocks.
- Real-time updates flow (subscribeToReleaseUpdates/subscribeToBlockUpdates) emits events for MVP blocks.
- Logs accessible for executed blocks; user-input pause/resume works for User action block.
**Tests**:
- Unit tests for block scheduler state transitions and dependency resolution for sequential/parallel edges.
- Service tests for create/start/pause/cancel with persisted state.
- Flow tests asserting update emission sequence.
**Status**: Not Started

## Stage 4: Integrations Expansion (GitHub/TeamCity/Maven)
**Goal**: Implement GitHub Action/Release blocks, TeamCity build tracking, and Maven Central status polling.
**Success Criteria**:
- GitHubAction and GitHubRelease blocks operational with parameter passing and outputs.
- TeamCity build trigger + polling with chain status aggregation; artifact links captured.
- Maven Central Portal polling by publication id until terminal state.
- Outputs are persisted and consumable by downstream blocks.
**Tests**:
- Mocked HTTP/clients for GitHub/TeamCity/Maven with deterministic responses.
- Service tests verifying outputs are produced and made available to next blocks.
- Error handling tests (timeouts, retries, API failures).
**Status**: Not Started

## Stage 5: UI Editor and Monitoring
**Goal**: Build a usable DAG editor for projects and a release monitor UI with streaming updates.
**Success Criteria**:
- Block editor supporting add/edit/delete blocks, connections (sequential/parallel), and container boundaries.
- Validation before saving projects; error messaging for invalid graphs.
- Release monitor shows live statuses/logs per block with controls (pause/restart/cancel).
- Compose Multiplatform UX tuned for Desktop and Web.
**Tests**:
- UI state reducer/unit tests for graph editing and validation.
- Snapshot tests for key UI screens.
- End-to-end test: create simple project -> start release -> observe status updates via mocked backend.
**Status**: Not Started

---

## Cross-cutting Tasks
- Observability: Structured logging and basic metrics for server.
- Security: AuthN/AuthZ across RPC calls, secure secret storage, input validation.
- CI: Add CI workflow to build, run tests, and publish artifacts.
- Docs: Update README with setup/run instructions and architecture overview.

## Dependencies and Approvals
- Before introducing new libraries (encryption, DI, test utilities) or upgrading versions, request approval.

## Risks and Mitigations
- Lack of tests: Start with Stage 1 tests to reduce future risk.
- Integration complexity: Use mocked clients and feature flags to ship increments.
- WebSocket/flow reliability: Backoff/retry and timeouts; provide fallbacks to polling if needed.

## Notes
- Follow the Development Guidelines: incremental progress, clear intent, no premature abstractions.
- Prefer multiple small PRs per stage.
