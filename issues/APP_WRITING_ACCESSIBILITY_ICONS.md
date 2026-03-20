# APP_WRITING_ACCESSIBILITY_ICONS — Missing Content Descriptions on Icons

Multiple meaningful (non-decorative) icons across the app have `contentDescription = null`,
making them invisible to screen readers.

## Issues

### EDITOR_03 — Lock banner icons (MEDIUM)
Lock icons in the editor banner have `contentDescription = null`. Add `"Locked"` or
`"Editing lock"`.

### EDITOR_13 — Remove parameter button "×" (LOW)
The `editor_prop_remove` button shows a bare multiplication sign with no tooltip or
content description. Screen readers announce "multiplication sign". Add
`contentDescription = "Remove parameter"`.

### CONNFORM_03 — Type selector dropdown and lock icons (MEDIUM)
- ArrowDropDown icon: add `"Open type selector"`
- Lock icon: add `"Locked"`

### CONNFORM_05 — Password toggle says "password" for non-password fields (MEDIUM)
Visibility toggles on Slack webhook URL, TeamCity token, and GitHub PAT all use
`"Hide password"` / `"Show password"`. The fields are not passwords.
**Fix:** Use field-specific descriptions (`"Show webhook URL"`, `"Show token"`, etc.)
or generalize to `"Show value"` / `"Hide value"`.

### CONNLIST_05 — Empty-state icons (MEDIUM)
48dp empty-state icons (search, link) have `contentDescription = null`. Add descriptions.

### CONNLIST_06 — Sort dropdown arrow (MEDIUM)
`contentDescription = null`. Add `"Change sort order"`.

### CONNLIST_07 — List item chevron (MEDIUM)
Navigation chevron has `contentDescription = null`. Add `"Open connection"` or set
the row's semantics to indicate navigability.

### AUTOMATION_07 — Schedule preset dropdown (MEDIUM)
Dropdown arrow in schedule preset selector has `contentDescription = null`.
Add `"Open preset list"`.

### AUTOMATION_08 — Toggle switches (MEDIUM)
Enable/disable toggles for schedules, webhooks, and Maven triggers have no accessible
labels. Add `"Enable schedule"` / `"Toggle webhook trigger"` etc.

### RELLIST_06 — Warning icon in error state (LOW)
Warning icon has `contentDescription = null`. Add `"Error"`.

### INVITES_04 — Empty-state mail icon (MEDIUM)
Primary visual element with `contentDescription = null`.

### AUDIT_07 — Empty-state history icon (LOW)
Decorative — acceptable as-is, but could be improved with semantic grouping.

### GLOBAL_09 — Settings chevron expand/collapse (LOW)
No indication of expanded/collapsed state for screen readers.

### GLOBAL_10 — Team-switcher dropdown arrow (LOW)
`contentDescription = null`. Add `"Switch team"`.

### GLOBAL_20 — Sign-out button state change (MEDIUM)
Content description stays `"Sign Out"` even during confirmation state.
Screen readers get no indication of the state change.
