# Future Work

## Feature Gaps

- **Multi-user auth** — Currently single hardcoded user. Design decision was "single-user now, design for multi-user later." Needs: user table, registration, role-based access, per-user project ownership.
- **Block restart from failed state** — `ExecutionEngine.restartBlock()` has a TODO stub. Need to reset a FAILED block to WAITING and re-enter the execution loop for that release.
- **Release re-run** — Clone a completed release with the same parameters and DAG snapshot. Useful for re-running a failed release pipeline after fixing the external issue.
- **Release deletion/archival** — No API to delete or archive old releases. They accumulate indefinitely.
- **Notification on completion** — Slack DM or email when a release finishes (succeeds or fails). Currently only visible via WebSocket/polling.
- **Scheduled/triggered releases** — Currently manual-only. Could add cron triggers or webhook-triggered releases (e.g., on Git tag push).
- **Block output → input wiring UI** — Template engine supports `{{blocks.blockId.outputKey}}` syntax, but the UI doesn't have a visual picker for wiring outputs to inputs.

## Quality Improvements

- **E2E integration tests** — Run server tests against real PostgreSQL (not H2). H2's PostgreSQL compatibility mode has gaps.
- **Concurrent release stress tests** — Multiple releases executing simultaneously, verify no state leakage between them.
- **WebSocket load testing** — Many simultaneous WebSocket subscribers on the same release.
- **Accessibility audit** — Compose Multiplatform UI may have screen reader / keyboard navigation gaps.
- **API security audit** — Pentest the REST API (see `pentest-api-deep` skill). Focus on: auth bypass, IDOR on release/project/connection IDs, webhook secret timing attacks, connection config credential exposure.
- **Config externalization cleanup** — `Config.kt` uses `System.getenv()` fallback; should switch to Ktor native YAML env var syntax (`$DB_URL` references in `application.yaml`) per project conventions.
