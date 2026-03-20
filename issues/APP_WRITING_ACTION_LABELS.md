# APP_WRITING_ACTION_LABELS — Vague or Misleading Button Labels

Buttons whose labels don't clearly communicate what action they perform.

## Issues

### TEAMDETAIL_01 — "Leave" confirmation button too vague (HIGH)
Bare `"Leave"` in a destructive confirmation. Could mean leave the page.
**Fix:** `"Leave Team"` (the string `teams_leave_title` already exists with this value).

### EDITOR_06 — "Fit" zoom button is misleading (MEDIUM)
"Fit" implies fit-to-content but actually resets zoom to 100%.
**Fix:** `"Reset view"` or `"100%"`.

### CONNLIST_08 — "Test" is ambiguous noun/verb (MEDIUM)
Bare "Test" could be a noun (test status) or verb (run test).
**Fix:** `"Test connection"` for clarity.

### AUTOMATION_09 — "I've saved it" first-person button (LOW)
Uses first person ("I've") which is unusual for UI buttons and ambiguous about where.
**Fix:** `"Done"` or `"I have saved the secret"`.

### TEAMDETAIL_09 — Mixed noun/verb toolbar labels (LOW)
"Audit Log" (noun) vs "Manage" (verb) in the same toolbar.
**Fix:** `"View Audit Log"` to match verb pattern, or accept the inconsistency.

### GLOBAL_08 — "Click again to sign out" is device-specific (MEDIUM)
"Click" assumes a pointer device. Keyboard/screen reader users don't "click."
**Fix:** `"Select again to sign out"` or `"Press again to confirm sign out"`.

### GLOBAL_13 — "Create new item" uses vague "item" (LOW)
**Fix:** `"Create new"` (drop "item").

### GLOBAL_14 — "Close dialog / deselect" compound description (LOW)
Slash-separated compound is structurally different from all other shortcut descriptions.
**Fix:** `"Close or deselect"` or `"Dismiss"`.

### GLOBAL_16 — "Show keyboard shortcuts" when already shown (LOW)
Ctrl+/ toggles the overlay, but description says "Show" (one-directional).
**Fix:** `"Toggle keyboard shortcuts"` or `"Keyboard shortcuts"`.

### TEAMMGMT_05 — "Delete" in Danger Zone (MEDIUM)
Section says "Delete Team" but button just says "Delete".
**Fix:** Use `"Delete Team"` as the button label.

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| TEAMDETAIL_01 | Fixed | "Leave" changed to "Leave team" |
| EDITOR_06 | Fixed | "Fit" changed to "Reset view" |
| CONNLIST_08 | Fixed | "Test" changed to "Test connection" |
| AUTOMATION_09 | Fixed | "I've saved it" changed to "Done" |
| GLOBAL_08 | Fixed | "Click again" changed to "Select again" |
| GLOBAL_13 | Fixed | "Create new item" changed to "Create new" |
| GLOBAL_14 | Fixed | "Close dialog / deselect" changed to "Close or deselect" |
| GLOBAL_16 | Fixed | "Show keyboard shortcuts" changed to "Toggle keyboard shortcuts" |
| TEAMMGMT_05 | Fixed | Danger zone button changed to "Delete Team" |
| TEAMDETAIL_09 | Minor | "Audit Log" vs "Manage" kept as-is (consistent within toolbar) |
