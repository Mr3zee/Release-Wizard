# APP_SECURITY_INSUFFICIENT_RATE_LIMITING

## Severity: Medium
## Category: Abuse Prevention / Denial of Service
## Affected Screens: All (TeamListScreen, TeamManageScreen, ConnectionListScreen, ProjectListScreen, MyInvitesScreen, ProjectAutomationScreen)

### Description

Rate limiting is only configured for the `login` endpoint. All other write endpoints lack rate limiting:

- **Team creation** — any user can rapidly create thousands of teams visible to all users
- **Project creation** — unlimited projects per team
- **Join requests** — spam join requests across all teams
- **Invite operations** — rapid accept/decline or invite spam
- **Connection test** — each test triggers outbound HTTP to external services (TeamCity, GitHub, Slack)
- **Schedule/trigger creation** — unlimited automation rules per project

### Impact

Authenticated users can flood the system with resources (teams, projects, triggers), exhaust external API rate limits via connection testing, or cause excessive DB/CPU load.

### Affected Locations

- `server/.../Application.kt:146-149` — only `login` rate limit configured
- `server/.../teams/TeamRoutes.kt` — no rate limits
- `server/.../projects/ProjectsRoutes.kt` — no rate limits
- `server/.../connections/ConnectionsRoutes.kt:87-95` — test endpoint unprotected
- `server/.../triggers/TriggerRoutes.kt` — no rate limits
- `server/.../schedules/ScheduleRoutes.kt` — no rate limits

### Recommendation

Add rate limit configurations for:
- Team creation: 10/minute per user
- Project creation: 10/minute per user
- Connection test: 5/minute per user
- Invite operations: 20/minute per user
- Trigger/schedule creation: 10/minute per user
- Global authenticated API: 100/minute per user
