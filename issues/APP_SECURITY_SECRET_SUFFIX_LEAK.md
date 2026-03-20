# APP_SECURITY_SECRET_SUFFIX_LEAK

## Severity: Medium
## Category: Secrets Management
## Affected Screens: ConnectionListScreen

### Description

The `mask()` function reveals the last 4 characters of every secret (tokens, webhook URLs). For short secrets (5-8 chars), this exposes 50-80% of the actual value. For Slack webhook URLs, the last 4 chars of the service path portion are always visible.

### Impact

Partial secret disclosure. An attacker with read-only team membership could reconstruct short secrets or significantly narrow brute-force space.

### Affected Locations

- `server/.../connections/ConnectionsService.kt:175-177` — `mask()` function

### Recommendation

Return a fixed placeholder like `"********"` regardless of secret length. Do not reveal any suffix characters.
