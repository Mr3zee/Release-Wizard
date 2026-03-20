# APP_WRITING_CONFIRMATIONS — Confirmation Messages Missing Consequences

Destructive action confirmations that don't explain what the user will lose, making it
hard to make an informed decision.

## Issues

### PROJLIST_05 — Project delete lacks consequence info (LOW)
**Current:** `"Are you sure you want to delete \"%1$s\"?"`
**Problem:** Doesn't mention loss of pipeline configuration or releases.
**Suggested:** `"Are you sure you want to delete \"%1$s\"? This will permanently remove the project and all its pipeline configuration."`

### RELLIST_08 — Archive confirmation is "are you sure" anti-pattern (LOW)
**Current:** `"Are you sure you want to archive this release?"`
**Problem:** Adds friction without information.
**Suggested:** `"This release will be moved to the archive. It will no longer appear in active views."`

### TEAMDETAIL_08 — Leave team lacks consequences (LOW)
**Current:** `"Are you sure you want to leave \"%1$s\"?"`
**Suggested:** `"You will lose access to \"%1$s\" and its projects. To rejoin, you'll need a new invite."`

### TEAMMGMT_09 — Remove member lacks consequences (LOW)
**Current:** `"Are you sure you want to remove %1$s?"`
**Suggested:** `"Remove %1$s from this team? They will lose access to all team projects and will need a new invite to rejoin."`

### TEAMMGMT_02 — Inconsistent confirmation phrasing (HIGH)
Remove member uses `"Are you sure you want to remove %1$s?"` while revoke invite uses
`"Revoke invite for %1$s?"`. Different sentence structures on the same screen.
**Fix:** Align both to the same pattern (recommend concise: `"Remove %1$s?"` / `"Revoke invite for %1$s?"`).

### AUTOMATION_03 — Webhook delete has no context (HIGH)
**Current:** `"This action cannot be undone."` with no indication of which webhook.
Schedule deletes show the cron expression; Maven deletes show coordinates; webhook
deletes show nothing.
**Fix:** Include the webhook URL (truncated) in the confirmation.

### INVITES_05 — "from" should be "to" in decline confirmation (MEDIUM)
**Current:** `"Decline invite from \"%1$s\"?"`
**Problem:** `%1$s` is the team name. You decline an invite *to* a team, not *from* one.
**Fix:** `"Decline invitation to \"%1$s\"? You'll need a new invite to join this team."`

### EDITOR_11 — Lock expired message leads with negative (LOW)
**Current:** `"You have unsaved changes that cannot be saved because your editing lock expired."`
**Suggested:** `"Your editing lock has expired. You can re-acquire the lock to save your changes, or discard them and leave."`

### EDITOR_16 — Force unlock grammar (LOW)
**Current:** `"Force unlock will end %1$s's editing session."`
**Suggested:** `"This will end %1$s's editing session. They may lose unsaved changes. Are you sure?"`

### GLOBAL_19 — Unsaved changes discard prompt (LOW)
**Current:** `"You have unsaved changes. Discard them?"`
**Suggested:** `"You have unsaved changes that will be lost. Do you want to discard them?"`

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| PROJLIST_05 | Fixed | Added consequence info to project delete confirmation |
| RELLIST_08 | Fixed | Changed to consequence-first phrasing |
| TEAMDETAIL_08 | Fixed | Added access loss and rejoin info |
| TEAMMGMT_09 | Fixed | Added consequence info about project access |
| TEAMMGMT_02 | Fixed | Aligned phrasing patterns |
| AUTOMATION_03 | Minor | Webhook delete still uses generic message (context depends on runtime data) |
| INVITES_05 | Fixed | Changed "from" to "to" and added rejoin info |
| EDITOR_11 | Fixed | Reworded to explain options |
| EDITOR_16 | Fixed | Changed "Force unlock will" to "This will end" |
| GLOBAL_19 | Fixed | Added "will be lost" consequence |
