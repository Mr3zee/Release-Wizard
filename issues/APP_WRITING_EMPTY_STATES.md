# APP_WRITING_EMPTY_STATES — Empty States Lacking Guidance

Empty state messages that tell users something is empty but don't explain what to do
next or why the area is empty.

## Issues

### TEAMMGMT_01 — No empty-state for pending invites / join requests (HIGH)
Sections show "Pending Invites (0)" / "Join Requests (0)" with nothing below.
`teams_no_pending_invites` ("No pending invites.") exists but is unused on this screen.
No equivalent exists for join requests.
**Fix:** Show `teams_no_pending_invites` when invites list is empty. Add
`teams_no_join_requests` = `"No pending join requests."` for the requests section.

### EDITOR_17 — Canvas hint doesn't match zero-block state (LOW)
**Current:** `"Drag from an output port to an input port to connect blocks"`
**Problem:** Shown even when canvas has zero blocks. Premature instruction.
**Fix:** When no blocks exist: `"Add blocks from the toolbar to get started"`.
When blocks exist but no edges: show the current connection instructions.

### TEAMDETAIL_07 — "No members yet" has no guidance hint (LOW)
**Problem:** Other empty states include hints (e.g., audit log). Members empty state
has no follow-up guidance.
**Fix:** Add `"Invite members from the Manage screen."` (for leads) or
`"Members will appear here once they join."` (for non-leads).

### TEAMLIST_08 — Empty state only suggests creating, not joining (LOW)
**Current:** `"No teams yet. Create one to get started."`
**Problem:** Doesn't acknowledge users who should be joining, not creating.
**Fix:** `"No teams yet. Create a team or ask a colleague to invite you."`

### CONNLIST_11 — Empty state doesn't explain what connections are (LOW)
**Current:** `"No connections yet. Add one to get started."`
**Fix:** `"No connections yet. Connections link external services like Slack, GitHub, and TeamCity to your release pipeline. Create one to get started."`

### EDITOR_10 — Template picker empty state not actionable (MEDIUM)
**Current:** `"No parameters or predecessor outputs available"`
**Problem:** Uses jargon ("predecessor outputs") and doesn't explain how to fix.
**Fix:** `"No templates available. Add project parameters or connect predecessor blocks with outputs to see options here."`

### AUTOMATION_13 — "Add one above" is spatially dependent (MEDIUM)
**Current:** All three empty hints say `"Add one above to get started."`
**Problem:** "Above" relies on layout position that may change.
**Fix:** `"Use the Add button to get started"` or `"Get started by adding one."`

### TEAMLIST_06 — Non-member cards give no textual reason for being dimmed (MEDIUM)
Cards at 70% opacity with no explanation for screen readers or sighted users.
**Fix:** Add `"Join this team to access its projects"` beneath member count.

### RELLIST_09 — Truncated UUID fallback for deleted project (LOW)
When `projectName` is null, shows raw truncated UUID like `"a3f8c2b1"`.
**Fix:** Use `"Unknown project"` fallback.

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| TEAMMGMT_01 | Fixed | Added empty states for pending invites and join requests |
| EDITOR_17 | Fixed | Added `editor_empty_canvas_no_blocks` for zero-block state |
| TEAMDETAIL_07 | Fixed | Added member hints for leads and non-leads |
| TEAMLIST_08 | Fixed | Changed to "Create a team or ask a colleague to invite you" |
| CONNLIST_11 | Fixed | Added description of what connections are |
| EDITOR_10 | Fixed | Improved template picker empty state |
| AUTOMATION_13 | Fixed | Changed "Add one above" to "Use the Add button" |
| TEAMLIST_06 | Fixed | Added `teams_join_hint` for non-member cards |
| RELLIST_09 | Fixed | Added `releases_unknown_project` fallback |
