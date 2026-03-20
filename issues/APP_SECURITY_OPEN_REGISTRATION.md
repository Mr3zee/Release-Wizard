# APP_SECURITY_OPEN_REGISTRATION

## Severity: Medium
## Category: Authentication / Access Control
## Affected Screens: LoginScreen

### Description

The registration endpoint is open to anyone — no invite code, email verification, CAPTCHA, or admin approval required. The first user gets ADMIN role; all subsequent users get USER role. The client openly exposes a "Create Account" toggle.

Additionally, the registration endpoint returns a distinct `409 Conflict` with "Username already taken" for duplicate usernames, enabling username enumeration.

### Impact

In an internet-facing deployment, anyone can create accounts, flooding the system with spam or gaining access to application features (viewing teams, requesting to join, etc.). Username enumeration reveals valid usernames.

### Affected Locations

- `server/.../auth/AuthRoutes.kt:72` — open registration endpoint
- `server/.../auth/AuthRoutes.kt:117-122` — username enumeration via 409
- `composeApp/.../auth/LoginScreen.kt:52` — "Create Account" toggle

### Recommendation

Consider adding: (a) admin-controlled registration, (b) invite-only registration, (c) CAPTCHA, or (d) a config flag to disable open registration. For enumeration, consider returning a uniform response.
