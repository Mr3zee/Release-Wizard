# APP_WRITING_RETRY_MISMATCH — Retry Buttons That Perform Wrong Action

"Retry" actions in error snackbars that reload data instead of retrying the failed
operation. Users expect "Retry" to repeat what just failed.

## Issues

### TEAMLIST_09 — Join request retry reloads team list (HIGH)
When a "Request to Join" fails, the snackbar "Retry" calls `viewModel.loadTeams()`
instead of retrying `viewModel.requestToJoin(teamId)`.
**Fix:** Differentiate load errors from action errors. For join-request errors,
either retry the join or omit the misleading "Retry" action.

### TEAMDETAIL_03 — Leave team retry refreshes data (MEDIUM)
When a leave-team action fails, the snackbar "Retry" calls `viewModel.refresh()`
instead of `viewModel.leaveTeam()`.
**Fix:** Make "Retry" actually retry the leave action, or change label to "Refresh".

### CONNLIST_04 — Connection test retry reloads list (MEDIUM)
When a connection test fails, the snackbar "Retry" calls `loadConnections()` instead
of re-testing the connection.
**Fix:** Show test failures with a "Retry test" action that calls
`testConnection(id)`.

### INVITES_01 — No success feedback after accept/decline (HIGH)
When a user accepts or declines an invite, the card just disappears with no snackbar
confirmation. If they accidentally decline, there's no way to know what happened.
**Fix:** Add success snackbar strings:
- `teams_invite_accepted`: `"Joined \"%1$s\" successfully."`
- `teams_invite_declined`: `"Invite from \"%1$s\" declined."`
