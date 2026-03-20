# APP_SECURITY_WEBSOCKET_SECURITY

## Severity: Low
## Category: WebSocket Security
## Affected Screens: ReleaseDetailScreen

### Description

Several WebSocket-related security concerns:

1. **CSRF not enforced on WS upgrade** — the CSRF plugin exempts WebSocket upgrade requests. The compensating Origin header check only validates when Origin is present and parseable. If Origin is absent or malformed, the check is bypassed (`if (originHost != null && ...)` passes when null).

2. **Auth failures not handled in reconnect** — `connectWithRetry` catches all exceptions silently. If the server rejects with 401/403, the client retries indefinitely with exponential backoff instead of triggering session expiry or stopping.

3. **Sequence gap on reconnect** — `ReleaseEvent.Snapshot` uses hardcoded `sequenceNumber = 0`, which could reset client sequence tracking and cause duplicate event processing.

### Impact

Theoretical cross-site WebSocket hijacking if Origin is absent (read-only). Wasted resources on indefinite retry of permanent auth failures. Transient data inconsistency on edge-case reconnections.

### Affected Locations

- `server/.../plugins/CsrfPlugin.kt:34` — WS exemption
- `server/.../releases/ReleaseWebSocketRoutes.kt:31-47` — Origin check fail-open
- `composeApp/.../releases/ReleaseDetailViewModel.kt:79` — silent exception swallowing
- `server/.../execution/ExecutionEngine.kt:83-94` — replay buffer lifecycle

### Recommendation

1. Make Origin check fail-closed (reject if absent or unparseable)
2. Distinguish retryable (network) vs permanent (401/403/404) errors in reconnect
3. Include accurate sequence numbers in snapshot events
