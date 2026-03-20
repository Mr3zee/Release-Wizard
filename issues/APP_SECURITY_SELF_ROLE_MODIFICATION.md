# APP_SECURITY_SELF_ROLE_MODIFICATION

## Severity: Medium
## Category: Authorization / Business Logic
## Affected Screens: TeamManageScreen, TeamDetailScreen

### Description

The `updateMemberRole` and `removeMember` server endpoints only check that the caller is a TEAM_LEAD. They do not prevent a TEAM_LEAD from targeting their own `userId`:

1. **Self-demotion via API** — a lead can call `PUT /teams/{id}/members/{self-id}` with role=COLLABORATOR. This is effectively irreversible without another lead's help.

2. **Self-removal via API** — a lead can call `DELETE /teams/{teamId}/members/{ownUserId}`. This bypasses the intended `leaveTeam` flow and creates misleading audit logs (`MEMBER_REMOVED` instead of `MEMBER_LEFT`).

The client UI correctly hides these options for the current user, but direct API calls bypass the UI.

### Impact

Team leads can accidentally or intentionally demote/remove themselves through direct API calls, creating inconsistent audit trails and potentially losing team management access.

### Affected Locations

- `server/.../teams/TeamService.kt:103-108` — `updateMemberRole` no self-check
- `server/.../teams/TeamService.kt:110-115` — `removeMember` no self-check

### Recommendation

Add server-side checks: `if (userId == session.userId) throw IllegalArgumentException("Use the leave endpoint")` for removeMember, and `throw ForbiddenException("Cannot change your own role")` for updateMemberRole.
