# APP_WRITING_THEME_AND_SIDEBAR — Sidebar and Global Navigation Copy Issues

## Issues

### GLOBAL_01 — "Theme: Auto" is ambiguous (MEDIUM)
**Current:** `"Theme: Auto"` / `"Theme: Light"` / `"Theme: Dark"`
**Problem:** "Auto" could mean many things. Users may not know it follows OS setting.
**Fix:** `"Theme: System"` or `"System (match OS)"`, `"Light"`, `"Dark"`.

### GLOBAL_17 — Collapsed sidebar shows "?" for no team (LOW)
No guidance or accessible description. Screen readers announce "question mark."
**Fix:** Use a group icon placeholder with tooltip `"No team selected"`.

### EDITOR_08 — Same tooltip for both sidebars (MEDIUM)
**Current:** `"Collapse panel"` / `"Expand panel"` used for both left and right panels.
**Fix:** `"Collapse toolbar"` / `"Expand toolbar"` for left;
`"Collapse properties"` / `"Expand properties"` for right.

### TEAMMGMT_07 — "Demote" has negative connotation (MEDIUM)
**Current:** `"Promote to Lead"` / `"Demote to Collaborator"`
**Fix:** `"Set as Lead"` / `"Set as Collaborator"` (neutral).

### TEAMMGMT_08 — "(You)" badge has redundant parentheses (LOW)
Badge styling already provides visual distinction. Parentheses are redundant inside a badge.
**Fix:** `"You"` without parentheses.

### INVITES_02 — "When a team lead invites you, it will appear here" (MEDIUM)
**Problem:** "it" is ambiguous; "team lead" may be inaccurate (other roles can invite).
**Fix:** `"When someone invites you to a team, the invitation will appear here."`

### INVITES_06 — "Invited by" passive voice (LOW)
Minor. Alternative: `"From %1$s"`.

### TEAMMGMT_04 — String key `teams_cancel_invite_confirmation` says "cancel" but text says "Revoke" (MEDIUM)
Maintenance hazard. **Fix:** Rename key to `teams_revoke_invite_confirmation`.

### TEAMMGMT_12 — Close button uses "Dismiss" instead of "Close" (LOW)
`common_dismiss` is used for form close buttons. "Dismiss" is semantically different
from "Close." Component-level concern.

### CONNFORM_09 — "Type cannot be changed after creation" (LOW)
Correct but negative tone. No guidance for wrong-type scenario.
**Fix:** `"Connection type is fixed after creation. To use a different type, create a new connection."`

### RELDETAIL_14 — Generic "Release" title bar (LOW)
`releases_detail_title` is just `"Release"` with no identifying info.
`releases_release_title` (`"Release %1$s"`) exists with the name but is unused.
**Fix:** Use `releases_release_title` with the release name.

### RELDETAIL_08 — Trailing colon in "Outputs:" (MEDIUM)
Only section label with a colon. Others ("Artifacts", "Error") don't use one.
**Fix:** `"Outputs"` (remove colon).

### RELDETAIL_10 — "Stopped" duration label is redundant and terse (LOW)
When a block is stopped, duration shows bare "Stopped" — redundant with status badge.
**Fix:** Show `"Stopped after: 2m 30s"` if elapsed time is available.

### RELDETAIL_13 — "2 of 3 approvals" is ambiguous direction (LOW)
Could mean received or remaining.
**Fix:** `"%1$d of %2$d approvals received"`.

### AUDIT_06 — "Lock forcibly released" is only 3-word label (LOW)
Minor inconsistency; all others are 2 words. Consider `"Lock force-released"`.

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| GLOBAL_01 | Fixed | "Theme: Auto" changed to "Theme: System" |
| GLOBAL_17 | Fixed | Collapsed sidebar shows tooltip "No team selected" instead of bare "?" |
| EDITOR_08 | Fixed | Split panel tooltips: toolbar vs properties |
| TEAMMGMT_07 | Fixed | "Promote to Lead"/"Demote to Collaborator" changed to "Set as Lead"/"Set as Collaborator" |
| TEAMMGMT_08 | Fixed | "(You)" changed to "You" |
| INVITES_02 | Fixed | Updated to "When someone invites you to a team, the invitation will appear here." |
| INVITES_06 | Minor | "Invited by" kept as-is |
| TEAMMGMT_04 | Fixed | Key renamed from `teams_cancel_invite_confirmation` to `teams_revoke_invite_confirmation` |
| TEAMMGMT_12 | Minor | "Dismiss" vs "Close" kept (component-level concern) |
| CONNFORM_09 | Fixed | "Type cannot be changed after creation" expanded to longer guidance |
| RELDETAIL_14 | Fixed | Title now uses `releases_release_title` with release ID |
| RELDETAIL_08 | Fixed | "Outputs:" changed to "Outputs" (removed colon) |
| RELDETAIL_10 | Minor | "Stopped" duration label kept |
| RELDETAIL_13 | Fixed | "2 of 3 approvals" changed to "2 of 3 approvals received" |
| AUDIT_06 | Fixed | "Lock forcibly released" changed to "Lock force-released" |
