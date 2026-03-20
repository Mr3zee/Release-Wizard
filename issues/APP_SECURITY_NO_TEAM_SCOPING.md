# APP_SECURITY_NO_TEAM_SCOPING

## Severity: Low
## Category: Authorization / Information Disclosure
## Affected Screens: ReleaseListScreen, ProjectListScreen, ConnectionListScreen, DagEditorScreen

### Description

Several API clients do not pass the active team ID when listing resources, despite the server supporting `teamId` query parameters:

- `ReleaseApiClient.listReleases()` — never sends `teamId`
- `ProjectApiClient.listProjects()` — never sends `teamId`
- `ConnectionApiClient.listConnections()` — never sends `teamId`
- `DagEditorViewModel.loadTeamConnections()` — loads all connections across teams

The server falls back to returning resources from ALL teams the user belongs to. The authorization is correct (users only see their teams' resources), but the UI shows a cross-team mix.

### Impact

Users in multiple teams see resources mixed across teams. In the DAG editor, connection dropdowns show connections from other teams (though the server rejects cross-team connection usage on save).

### Affected Locations

- `composeApp/.../api/ReleaseApiClient.kt:18-32`
- `composeApp/.../api/ProjectApiClient.kt:14-25`
- `composeApp/.../api/ConnectionApiClient.kt:14-27`
- `composeApp/.../editor/DagEditorViewModel.kt:152-162`

### Recommendation

Pass `activeTeamId` as a query parameter in all list API calls.
