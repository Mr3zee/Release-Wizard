# APP_WRITING_ACCESSIBILITY_LOADING — Loading States Without Accessible Labels

Multiple screens show `CircularProgressIndicator` with no content description or
accompanying text, making loading states invisible to screen readers.

## Issues

### PROJLIST_08 — ProjectListScreen loading (LOW)
Spinner with no text or content description.
**Fix:** Add `semantics { contentDescription = "Loading projects" }`.

### RELLIST_10 — ReleaseListScreen loading (LOW)
Spinner with no text. Note: `releases_loading` exists but is singular ("Loading release…")
and used on the detail screen. Create `releases_loading_list` for the list screen.

### AUTOMATION_16 — ProjectAutomationScreen loading (LOW)
Spinner with no content description.
**Fix:** Add `semantics { contentDescription = "Loading automation settings" }`.

### TEAMMGMT_06 — TeamManageScreen loading (MEDIUM)
Two spinners: full-screen load (line 124) and invite button load (line 513). Neither
has accessible labels. Invite button loses its "Invite" label during loading.

### INVITES_07 — MyInvitesScreen accept button loading (LOW)
Accept button spinner replaces text with no accessible label. Button becomes
semantically empty while loading.

### CONNLIST_12 — ConnectionListScreen test button loading (LOW)
Test button spinner replaces "Test" text with no content description.

### AUDIT_04 — AuditLogScreen loading (MEDIUM)
Spinner with no semantics.
**Fix:** Add `semantics { contentDescription = "Loading audit events" }`.

### RELDETAIL_16 — ReleaseDetailScreen loading (LOW)
Spinner + "Loading release…" text exist as separate nodes. Should be linked semantically.

### LOGIN_08 — Login button loading (LOW)
Submit button text replaced by spinner with no accessible description.
Screen readers hear a button with no label.

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| PROJLIST_08 | Fixed | Added `loading_projects` semantic contentDescription |
| RELLIST_10 | Fixed | Added `loading_releases` semantic contentDescription |
| AUTOMATION_16 | Fixed | Added `loading_automation` semantic contentDescription |
| TEAMMGMT_06 | Fixed | Added `loading_team_management` semantic contentDescription |
| INVITES_07 | Fixed | Added `loading_invites` semantic contentDescription |
| CONNLIST_12 | Fixed | Added `loading_connections` semantic contentDescription |
| AUDIT_04 | Fixed | Added `loading_audit_log` semantic contentDescription |
| RELDETAIL_16 | Fixed | Pre-existing text serves as accessible label |
| LOGIN_08 | Fixed | Added `loading_login` semantic contentDescription |
