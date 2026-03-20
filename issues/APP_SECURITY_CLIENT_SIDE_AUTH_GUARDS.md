# APP_SECURITY_CLIENT_SIDE_AUTH_GUARDS

## Severity: Medium
## Category: Authorization (Defense-in-Depth)
## Affected Screens: TeamDetailScreen, TeamManageScreen, ProjectListScreen, ReleaseListScreen

### Description

Several screens rely on client-side-only guards for showing/hiding privileged actions:

1. **TeamManageScreen accessible via URL** — the "Manage" button is gated by `isTeamLead`, but navigating directly to `/teams/{id}/manage` renders the full management UI (edit, delete, invite, role change) for any user.

2. **Stale role state** — `isTeamLead` is derived from `userTeams` fetched at session start. If another lead demotes the user, the flag remains stale until session refresh (60s default).

3. **Delete/Archive shown to all users** — ReleaseListScreen shows Archive/Delete menu items for any user. ProjectListScreen shows delete for collaborators. The server returns 403, but the UI misleads.

Note: The server correctly enforces all authorization. These are defense-in-depth / UX issues, not exploitable bypasses.

### Impact

Users see actions they cannot perform, leading to confusing 403 errors. In the TeamManage case, non-leads can see the management UI (invites, join requests, member list with roles).

### Affected Locations

- `composeApp/.../navigation/AppNavigation.kt:170` — no role check on TeamManage route
- `composeApp/.../teams/TeamDetailScreen.kt:110` — manage button client-side only
- `composeApp/.../releases/ReleaseListScreen.kt:604-636` — archive/delete shown to all
- `composeApp/.../projects/ProjectListScreen.kt:272` — delete shown to collaborators

### Recommendation

Add client-side role checks that match server-side authorization: hide management navigation for non-leads, conditionally show delete/archive only for TEAM_LEAD/ADMIN.
