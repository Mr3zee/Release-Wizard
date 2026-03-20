# APP_WRITING_TERMINOLOGY — Inconsistent Terminology and Voice

Multiple instances where the same concept uses different terms or grammatical patterns.

## Issues

### LOGIN_04 — "Sign in" / "Register" / "Create Account" for same action (MEDIUM)
Three different terms for the registration action across the login screen.
**Fix:** Use "Create account" consistently (matching the primary button).

### LOGIN_05 — "Sign in to continue" on initial landing (MEDIUM)
"Continue" implies the user was interrupted, but this is the default landing page.
**Fix:** `"Sign in to your account"`.

### RELDETAIL_03 — Stop vs Cancel distinction unclear on buttons (MEDIUM)
Both top-bar buttons ("Stop", "Cancel") lack tooltips explaining that Stop is
resumable and Cancel is permanent. Users must click to discover the difference.
**Fix:** Add tooltips: "Pause this release (you can resume later)" for Stop,
"Permanently cancel this release" for Cancel.

### RELDETAIL_04 — "cancelled" in a "stop" message (MEDIUM)
`releases_stop_block_body` says external builds will be "cancelled" in a Stop context.
**Fix:** Use "stopped" consistently within stop messages.

### RELDETAIL_05 — "step" vs "block" in approval message (MEDIUM)
`releases_approve_default_message` uses "step" but the app's core unit is "block".
**Fix:** `"Approve this block to continue the release pipeline."`

### CONNLIST_03 — "Connection test succeeded" vs "Test failed" (MEDIUM)
Asymmetric phrasing for success vs failure.
**Fix:** `"Connection test succeeded: %1$s"` / `"Connection test failed: %1$s"`.

### AUDIT_02 — "Release rerun" breaks past-tense pattern (MEDIUM)
All other actions use past tense ("started", "cancelled"). "rerun" is ambiguous.
**Fix:** `"Release re-run"` or `"Release restarted"`.

### AUDIT_03 — "User logged in" includes redundant subject (MEDIUM)
All other actions omit the subject ("Team created", not "User created team").
**Fix:** `"Logged in"` and `"Registered"`.

### EDITOR_15 — "Pre-gate" / "Post-gate" casing vs "Approval Gates" (LOW)
Section header uses title case; sub-labels use sentence case with hyphen.

### RELDETAIL_12 — Gate phase wording inconsistency (LOW)
Pre-gate is a single phrase; post-gate uses an em dash joining two clauses.
**Fix:** Make structurally parallel.

### PROJLIST_09 — "No results match your search" vs "Create one to get started" (LOW)
Tonal shift: warm second-person vs impersonal third-person on same screen.

### INVITES_09 — "My Invites" title vs "pending invites" in empty state (LOW)
Title could be "Pending Invites" to match.

### CONNFORM_11 — "Configuration" in section headers vs "Connection" in title (LOW)
**Fix:** Use "Settings" or "Details" in section headers.
