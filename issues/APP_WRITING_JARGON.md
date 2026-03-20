# APP_WRITING_JARGON — Technical Language for Non-Technical Users

Terms and phrases that assume developer knowledge. While the target audience is
developers, some strings could be clearer without sacrificing precision.

## Issues

### EDITOR_04 — Webhook hint uses "POST" and env var name (MEDIUM)
**Current:** `"Build can POST status updates to RELEASE_WIZARD_WEBHOOK_URL"`
**Fix:** `"When enabled, the build will report its status back to Release Wizard automatically"`

### AUTOMATION_10 — "Cron Expression" with no format explanation (LOW)
**Current:** Label `"Cron Expression"`, hint `"e.g. 0 9 * * 1-5"`
**Fix:** Expand hint: `"e.g. 0 9 * * 1-5 (min hour day month weekday)"`.

### EDITOR_14 — Template tooltip mentions "${" syntax (LOW)
**Current:** `"Insert template (or type ${)"`
**Fix:** `"Insert a variable reference"`. Move keyboard trigger info to docs/help.

### AUDIT_10 — "Trigger fired" may confuse non-developers (LOW)
**Fix:** `"Trigger activated"` or `"Trigger executed"`.

### CONNLIST_13 — "Webhook" terminology (LOW)
While standard for the audience, could use `"Slack endpoint"` or `"Notification URL"`
if audience broadens. No change needed currently.

### RELDETAIL_15 — "No status updates received" (LOW)
**Current:** `"No status updates received yet"` (running) /
`"No status updates were reported by this build"` (finished)
**Fix:** Running: `"Waiting for build status from the external service"` /
Finished: `"The external build did not report status updates"`.

### TEAMLIST_02 — Join confirmation lacks next-step info (MEDIUM)
**Current:** `"Join request submitted"`
**Fix:** `"Join request submitted. A team admin will review your request."`

### GLOBAL_12 — "Focus search" is developer jargon (LOW)
**Fix:** `"Search"` or `"Go to search"`.

### AUTOMATION_17 — "Parameter Key" is vague (LOW)
**Current:** `"Parameter Key"` with hint `"e.g. version"`
**Fix:** `"Release parameter key"` with expanded hint explaining the purpose.

## Resolution

**Status:** SKIPPED

All 9 issues skipped per user request — app is for tech users, jargon is intended and appropriate for the target audience.
