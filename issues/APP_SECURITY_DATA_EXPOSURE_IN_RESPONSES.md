# APP_SECURITY_DATA_EXPOSURE_IN_RESPONSES

## Severity: Medium
## Category: Data Exposure
## Affected Screens: ReleaseListScreen, ReleaseDetailScreen

### Description

Multiple API responses include sensitive data that should be redacted:

1. **Release list returns full DAG snapshots** — each `ActionBlock` contains `connectionId` (UUID referencing Slack webhooks, TeamCity servers, GitHub repos). The list endpoint returns all releases with full DAG snapshots, leaking infrastructure topology.

2. **Release parameters exposed in list** — `Release.parameters` (key-value pairs) may contain sensitive values (API keys, internal URLs). The list endpoint returns the full parameter list even though the UI doesn't display them.

3. **Block execution outputs may contain secrets** — the server's `TemplateEngine.resolveParameters` resolves expressions like `{{connection.token}}` into actual values. These resolved values flow into block outputs broadcast over WebSocket.

4. **Release parameters have no "secret" concept** — the `Parameter` model has no `secret: Boolean` field, so all values are sent to all WebSocket subscribers.

### Impact

Sensitive configuration values, infrastructure details, and potentially connection secrets are transmitted to clients that only need summary information.

### Affected Locations

- `shared/.../model/Release.kt:11-12` — `dagSnapshot` and `parameters` in Release model
- `server/.../releases/ReleasesRoutes.kt:24-39` — list returns full objects
- `composeApp/.../releases/ReleaseDetailScreen.kt:533-546` — block outputs displayed
- `shared/.../model/Block.kt:33` — connectionId in ActionBlock

### Recommendation

1. Return lightweight DTOs for list endpoints (omit `dagSnapshot`, `parameters`)
2. Add `secret: Boolean` to `Parameter` model; redact secret values in responses
3. Classify and redact block outputs derived from connection secrets before WebSocket broadcast
