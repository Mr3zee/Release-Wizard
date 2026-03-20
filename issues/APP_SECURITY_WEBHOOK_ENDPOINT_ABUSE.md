# APP_SECURITY_WEBHOOK_ENDPOINT_ABUSE

## Severity: Medium
## Category: Denial of Service / Abuse
## Affected Screens: ProjectAutomationScreen

### Description

The unauthenticated webhook endpoint (`POST /api/v1/triggers/webhook/{triggerId}`) has no rate limiting. While it requires a valid Bearer secret to fire a release, every request still triggers a database lookup and SHA-256 hash computation. Trigger IDs (UUIDs) are displayed in the UI and webhook URLs are shown to team members.

Additionally, there are no per-project limits on the number of webhook triggers, schedules, or Maven triggers. An attacker could create thousands of schedules firing at the 5-minute minimum interval.

### Impact

An attacker who knows a trigger ID can flood the webhook endpoint, causing excessive DB reads and hash computations. With a leaked secret, they can trigger unlimited releases. Unlimited automation rules compound the risk.

### Affected Locations

- `server/.../triggers/TriggerRoutes.kt:76-108` — unauthenticated webhook endpoint
- `server/.../triggers/TriggerService.kt:48-70` — no per-project trigger limits
- `server/.../schedules/ScheduleService.kt:46-77` — no per-project schedule limits
- `server/.../mavenpublication/MavenTriggerService.kt:47-73` — no per-project Maven trigger limits

### Recommendation

1. Add per-trigger-ID rate limiting (e.g., 10 requests/minute)
2. Add per-project caps (e.g., max 50 of each trigger type)
3. Add IP-based rate limiting for failed webhook auth attempts
