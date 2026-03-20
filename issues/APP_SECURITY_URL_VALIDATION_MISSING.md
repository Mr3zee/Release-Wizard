# APP_SECURITY_URL_VALIDATION_MISSING

## Severity: Medium
## Category: Input Validation / SSRF
## Affected Screens: ConnectionFormScreen, ProjectAutomationScreen, ReleaseDetailScreen

### Description

Multiple URL inputs lack proper scheme and format validation:

1. **TeamCity server URL** — accepts any non-blank string with no scheme validation. `file://`, `ftp://`, or schemeless URLs are accepted.

2. **Slack webhook URL** — validated to start with `https://hooks.slack.com/` only in the `testSlack` path. On create/update, any arbitrary URL is accepted and stored. When pipeline notifications fire, the server POSTs to attacker-controlled endpoints.

3. **Maven repository URL** — both client and server accept `http://` URLs. The poller fetches metadata over plaintext HTTP every 5 minutes, enabling MitM to inject fake version strings that trigger releases.

4. **Sub-build URLs** — `uriHandler.openUri(url)` called on `subBuild.buildUrl` without scheme validation. A compromised CI system could inject `javascript:` or phishing URLs.

### Impact

SSRF via non-HTTP schemes, data exfiltration via arbitrary webhook URLs, MitM on Maven polling, potential phishing via sub-build links.

### Affected Locations

- `composeApp/.../connections/ConnectionFormScreen.kt:380-387,519` — TeamCity URL
- `server/.../connections/ConnectionTester.kt:44` — Slack validation only in test
- `server/.../connections/ConnectionsService.kt:69-84` — no URL validation on save
- `server/.../mavenpublication/MavenTriggerService.kt:88` — accepts http://
- `composeApp/.../releases/SubBuildsSection.kt:182` — unvalidated openUri

### Recommendation

1. Validate all URLs require `https://` scheme (allow `http://` only for localhost in dev)
2. Validate Slack webhook URL format on create/update, not just test
3. Restrict Maven URLs to `https://` only
4. Validate sub-build URLs to `https://` before opening
