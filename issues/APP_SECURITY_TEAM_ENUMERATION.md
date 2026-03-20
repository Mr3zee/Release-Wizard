# APP_SECURITY_TEAM_ENUMERATION

## Severity: Medium
## Category: Authorization / Information Disclosure
## Affected Screens: TeamListScreen, TeamDetailScreen

### Description

The `listTeams` endpoint returns **all teams in the system** to any authenticated user, regardless of membership. Any logged-in user can enumerate all team names, descriptions, and member counts. Similarly, `getTeamDetail` returns team metadata (name, description, creation date) to non-members — only the member list is conditionally hidden.

### Impact

An authenticated user can discover every team, their descriptions (which may contain sensitive organizational info), and member counts. Combined with the lack of rate limiting on team creation, an attacker could also flood the system with teams visible to all users.

### Affected Locations

- `server/.../teams/TeamService.kt:53-63` — `listTeams` returns all teams
- `server/.../teams/TeamService.kt:65-71` — `getTeamDetail` accessible to non-members
- `server/.../teams/TeamRoutes.kt:23-28` — no scoping on list endpoint

### Recommendation

If team discovery is intentional (for join requests), document this as a deliberate design decision. If teams should be private, add a visibility model (public vs. private teams). Consider limiting exposed metadata for non-members.

### Feedback

This is a deliberate design decision. Don't change
