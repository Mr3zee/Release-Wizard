# APP_SECURITY_SSRF_DNS_REBINDING

## Severity: Medium
## Category: Server-Side Request Forgery (SSRF)
## Affected Screens: ConnectionFormScreen, ConnectionListScreen, ProjectAutomationScreen

### Description

The SSRF protection in `validateUrlNotPrivate` resolves DNS and checks the IP at validation time. The actual HTTP request resolves DNS again separately. A DNS rebinding attack can return a public IP during validation and a private/internal IP during the actual request. The dual-check approach (both `validateUrlNotPrivate` and `SsrfProtection` plugin) significantly raises the bar but both independently resolve DNS, creating a narrow TOCTOU window.

This affects:
- TeamCity server URLs in connection testing
- GitHub API calls in connection testing
- Maven repository URL polling

### Impact

A sophisticated attacker with a controlled DNS server could potentially force the server to make requests to internal network addresses.

### Affected Locations

- `server/.../connections/ConnectionTester.kt:345-363` — `validateUrlNotPrivate`
- `server/.../plugins/SsrfProtectionPlugin.kt:17-28` — second DNS resolution
- `server/.../mavenpublication/MavenMetadataFetcher.kt:37-43` — Maven URL validation

### Recommendation

Configure the HTTP client with a custom DNS resolver that pins the resolved IP from the validation check, or use a network-level proxy that blocks private IP ranges.
