# APP_SECURITY_SECRETS_IN_CLIENT_MEMORY

## Severity: Low
## Category: Secrets Management / Memory Safety
## Affected Screens: LoginScreen, ConnectionFormScreen, ProjectAutomationScreen

### Description

Sensitive values persist in Compose state and client memory longer than necessary:

1. **Login password** — stored in `mutableStateOf("")`. After successful login, the string remains in JVM heap until GC. No explicit clearing.

2. **Connection secrets** — tokens and webhook URLs stored as plain `String` in Compose state. No clearing on form exit via `DisposableEffect`.

3. **Webhook secret** — stored in `mutableStateOf<String?>` and automatically copied to system clipboard. Persists in clipboard and accessibility tree until user dismisses.

4. **Password in serializable DTOs** — `LoginRequest` and `RegisterRequest` are data classes with auto-generated `toString()` that includes the password in cleartext.

### Impact

On compromised desktops or via memory dumps, plaintext secrets could be extracted. JVM's immutable `String` means secrets cannot be reliably zeroed.

### Affected Locations

- `composeApp/.../auth/LoginScreen.kt:49-51` — password state
- `composeApp/.../connections/ConnectionFormScreen.kt:65-69` — secret state
- `composeApp/.../automation/ProjectAutomationScreen.kt:120-145` — webhook secret + clipboard
- `shared/.../api/AuthDtos.kt:10-19` — password in toString()

### Recommendation

1. Clear password/secret state to empty string after successful submission
2. Override `toString()` in auth DTOs to redact passwords
3. Clear clipboard after a timeout for webhook secrets
4. Use `DisposableEffect` to clear secret state on form disposal
