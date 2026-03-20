# APP_SECURITY_ACCOUNT_LOCKOUT_IN_MEMORY

## Severity: Medium
## Category: Authentication / Brute Force Protection
## Affected Screens: LoginScreen

### Description

`AccountLockoutService` stores lockout state in an in-memory `ConcurrentHashMap`. State is lost on server restart and not shared across multiple instances. An attacker can reset lockout counters by waiting for a restart or bypass lockout in multi-instance deployments by targeting different instances.

### Impact

Brute force protection can be bypassed in multi-instance deployments or across restarts.

### Affected Locations

- `server/.../auth/AccountLockoutService.kt:19-20` — in-memory ConcurrentHashMap

### Recommendation

For production multi-instance deployments, persist lockout state in the database or Redis.
