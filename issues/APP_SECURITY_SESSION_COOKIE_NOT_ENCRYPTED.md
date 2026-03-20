# APP_SECURITY_SESSION_COOKIE_NOT_ENCRYPTED

## Severity: Medium
## Category: Session Management / Data Exposure
## Affected Screens: All (server-side)

### Description

The session cookie uses `SessionTransportTransformerMessageAuthentication` (HMAC signing) but not `SessionTransportTransformerEncrypt`. The session payload (username, userId, role, csrfToken, timestamps) is integrity-protected against tampering but transmitted in cleartext (Base64-encoded). Anyone who can read the cookie value can see the user's role, userId, username, and CSRF token.

### Impact

Information disclosure of session contents. While httpOnly and SameSite mitigate XSS-based theft, session data is readable by the user or any local process with cookie access.

### Affected Locations

- `server/.../Application.kt:166` — session transport configuration

### Recommendation

Chain `SessionTransportTransformerEncrypt` before the message authentication transform, using a separate encryption key.
