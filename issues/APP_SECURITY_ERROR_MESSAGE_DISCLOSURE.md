# APP_SECURITY_ERROR_MESSAGE_DISCLOSURE

## Severity: Low
## Category: Information Disclosure
## Affected Screens: All screens

### Description

Multiple paths expose internal details in error messages:

1. **Connection test errors** — `ConnectionTester` returns raw `e.message` in test results (e.g., `"Failed to connect: ${e.message}"`), which can contain DNS resolution failures with internal hostnames, IP addresses, TLS certificate details.

2. **Block execution errors** — displayed verbatim in ReleaseDetailScreen with a "Copy to Clipboard" button. May contain DB connection strings, stack traces, internal file paths.

3. **Server validation errors** — `IllegalArgumentException` messages from `require` blocks (including internal IDs like block/connection UUIDs) are passed through `ErrorResponse.error` and displayed directly in UI snackbars.

4. **Invite errors** — different error messages for "invite not found" vs "not for you" enable oracle-based enumeration of valid invite IDs.

### Impact

Internal infrastructure details (hostnames, IPs, paths, UUIDs) leak through error messages, aiding reconnaissance.

### Affected Locations

- `server/.../connections/ConnectionTester.kt:76,95` — raw exception messages
- `composeApp/.../releases/ErrorDetailSection.kt:70-86` — verbatim error display
- `server/.../Application.kt:211-221` — passes `cause.message` for IllegalArgumentException
- `server/.../teams/TeamService.kt:221` — oracle in invite errors

### Recommendation

1. Return generic error messages to clients; log detailed errors server-side only
2. Sanitize block execution errors before persisting
3. Use uniform error messages where enumeration is a concern
