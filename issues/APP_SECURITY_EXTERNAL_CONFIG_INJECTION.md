# APP_SECURITY_EXTERNAL_CONFIG_INJECTION

## Severity: Medium
## Category: Injection / Path Traversal
## Affected Screens: DagEditorScreen, ConnectionFormScreen, ConnectionListScreen

### Description

Multiple code paths construct URLs to external services using user-supplied values without consistent encoding:

1. **GitHub owner/repo in testGitHub** — `ConnectionTester.testGitHub` uses string interpolation: `"https://api.github.com/repos/${config.owner}/${config.repo}"` without `encodePathSegment`. Compare with `fetchGitHubWorkflows` which correctly uses `encodePathSegment`.

2. **External configId** — `fetchExternalConfigParameters` passes `configId` (TeamCity `buildTypeId` or GitHub workflow file) through the API chain. A crafted `configId` with `../../` could manipulate the URL constructed for external API calls.

3. **Maven version string** — the version string from Maven metadata XML is used as-is as a parameter value. An attacker controlling a Maven repo could inject special characters that propagate into build parameters.

### Impact

Path traversal could access unintended external API endpoints using the connection's bearer token. Crafted version strings could cause unexpected behavior in downstream executors.

### Affected Locations

- `server/.../connections/ConnectionTester.kt:82` — missing `encodePathSegment` in testGitHub
- `server/.../connections/ConnectionsService.kt:140-148` — configId passed through
- `server/.../mavenpublication/MavenPollerService.kt:80-83` — unvalidated version string
- `composeApp/.../api/ConnectionApiClient.kt:71-82` — configId in URL

### Recommendation

1. Apply `encodePathSegment` consistently in all URL construction (especially `testGitHub`)
2. Validate `configId` matches expected patterns before passing to external services
3. Validate Maven version strings against a character allowlist and length limit
