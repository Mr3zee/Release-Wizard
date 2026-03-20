# APP_SECURITY_NAME_SANITIZATION

## Severity: Low
## Category: Input Validation
## Affected Screens: TeamListScreen, ProjectListScreen, LoginScreen

### Description

User-supplied names (team names, project names, usernames) are not trimmed or sanitized:

1. **No trimming** — leading/trailing whitespace is preserved. `"admin"` and `"admin "` create distinct entities with visually similar names.

2. **No character filtering** — control characters, Unicode direction overrides (RTL/LTR marks), zero-width characters, and homoglyphs are accepted. A team named with zero-width characters appears blank.

3. **Username normalization** — no `trim()` on login or registration. A user who registers with trailing spaces would fail to log in without them.

### Impact

Visual impersonation through confusingly similar names. Login failures from accidental whitespace. If data is ever rendered outside Compose (emails, admin dashboards), stored XSS could become relevant.

### Affected Locations

- `server/.../teams/TeamService.kt:43-46` — team name only checks isNotBlank and length
- `server/.../projects/ProjectsService.kt:56` — project name isNotBlank only
- `server/.../auth/AuthRoutes.kt:31,73` — no username trim/normalize

### Recommendation

1. Trim all names server-side before validation
2. Reject control characters (Unicode categories Cc, Cf)
3. Normalize usernames to lowercase or restrict to `[a-zA-Z0-9_-]`
