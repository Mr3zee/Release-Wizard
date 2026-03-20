# APP_WRITING_PUNCTUATION — Ellipsis and Punctuation Inconsistencies

Mixed usage of Unicode ellipsis (U+2026) vs ASCII triple-dots across the app.

## Issues

### EDITOR_12 — Mixed ellipsis characters (LOW)
`"Loading..."` and `"Saving..."` use ASCII `...` while `"Select connection…"` and
`"Search configurations…"` use Unicode `…` (U+2026).

### RELDETAIL_09 — Mixed ellipsis in release strings (LOW)
`releases_loading` uses Unicode ellipsis; `releases_reconnecting` and `sub_builds_discovering`
use ASCII dots.

### RELLIST_12 — Search placeholder ASCII dots (LOW)
`"Search releases..."` uses ASCII dots; other search fields may vary.

### INVITES_03 — Trailing period on empty state heading (MEDIUM)
`"No pending invites."` has a trailing period. UI headings conventionally omit them.
**Fix:** `"No pending invites"`.

### AUTOMATION_12 — Embedded checkmark in "Valid ✓" (LOW)
Only string resource that embeds a Unicode symbol directly. Other status indicators use
icons or color. May render differently across platforms.
**Fix:** Use a Compose Icon next to "Valid" text instead.

### GLOBAL_07 — Inconsistent punctuation in error messages (LOW)
Some errors end with periods, others don't. Adopt consistent convention.

**Fix for ellipsis issues:** Standardize on Unicode ellipsis (U+2026) everywhere.

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| EDITOR_12 | Fixed | Standardized all ellipsis to Unicode (U+2026) |
| RELDETAIL_09 | Fixed | Same standardization applied |
| RELLIST_12 | Fixed | Same standardization applied |
| INVITES_03 | Fixed | Removed trailing period from "No pending invites" |
| AUTOMATION_12 | Fixed | Removed embedded checkmark from "Valid", added Check icon in code |
| GLOBAL_07 | Fixed | Standardized punctuation across error messages |
