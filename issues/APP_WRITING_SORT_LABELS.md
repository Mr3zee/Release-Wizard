# APP_WRITING_SORT_LABELS — Misleading Sort Option Labels

## Issues

### PROJLIST_01 — "Newest first" sorts by updatedAt, not createdAt (MEDIUM)
**Current:** `"Newest first"` / `"Oldest first"`
**Problem:** Implies creation date but code sorts by `updatedAt`. Users who recently
edited an old item will see it jump to the top under "Newest first".
**Fix:** `"Recently updated"` / `"Least recently updated"`.

### CONNLIST_09 — Same issue on ConnectionListScreen (LOW)
Same labels, same `updatedAt` sort.

### RELLIST_11 — Redundant timestamp in release list items (LOW)
Title includes `"$displayName — ${formatTimestamp(startedAt)}"` and subtitle shows
`"Started: ${formatTimestamp(startedAt)}"` — same timestamp twice.
**Fix:** Remove timestamp from title; let subtitle carry temporal info.

### AUTOMATION_04 — "Next run" label shows schedule description (MEDIUM)
**Current:** `"Next run: Every weekday at 9 AM"`
**Problem:** `"Next run"` should show a specific timestamp, not a schedule description.
**Fix:** Rename label to `"Schedule"` or compute actual next-run timestamp.

### PROJLIST_10 — Sort button has no accessible context (LOW)
Sort dropdown button only shows current sort label with no `contentDescription`.
Screen readers hear "Name A to Z" with no context that this is a sort control.
**Fix:** Add semantic description: `"Sort order: Name A to Z. Click to change."`

## Fix

For sort labels: Replace `"Newest first"` / `"Oldest first"` with `"Recently updated"` /
`"Least recently updated"` across all list screens. Apply to `common_sort_newest` and
`common_sort_oldest` in `strings.xml` — single change fixes all screens.
