# APP_WRITING_CAPITALIZATION — Inconsistent Casing Across UI

The app mixes Title Case and sentence case for buttons, headings, and labels without
a clear convention.

## Issues

### PROJLIST_02 — "Create project" vs "New Project" vs "Delete Project" (MEDIUM)
FAB uses sentence case, form title uses title case.
**Fix:** Pick one casing convention. Recommend sentence case per Material Design guidelines.

### EDITOR_05 — Canvas block labels use all-lowercase for brand names (MEDIUM)
`block_label_*` strings: `"teamcity build"`, `"github action"`, etc.
Brand names (TeamCity, GitHub, Slack) should always retain proper capitalization.
**Fix:** At minimum `"TeamCity build"`, `"GitHub action"`, `"Slack message"`.

### TEAMLIST_03 — "Request to Join" title case (LOW)
Other action buttons use sentence case ("Clear search", "Create").
**Fix:** `"Request to join"`.

### TEAMLIST_04 — "My Invites" title case (LOW)
Other labels use sentence case.
**Fix:** `"My invites"` for consistency.

### CONNLIST_10 — "Create connection" vs "Delete Connection" (LOW)
Active UI uses sentence case; the unused `connections_delete_title` uses title case.

### CONNFORM_07 — "Polling Interval (seconds)" embeds unit in label (MEDIUM)
Only label to embed unit hint in parentheses. Supporting text already provides this info.
**Fix:** Shorten to `"Polling Interval"` (or `"Polling interval"` in sentence case).

### RELLIST_01 — "Start release" vs "Create Release" vs "Start Release" (MEDIUM)
Same action has three different labels with inconsistent casing and verb.
**Fix:** Unify to `"Start Release"` or `"Start release"` everywhere.

### LOGIN_10 — "Sign In" button vs "Sign in" toggle link (LOW)
Mixed capitalization for the same phrase.
**Fix:** Adopt sentence case everywhere ("Sign in", "Create account").
