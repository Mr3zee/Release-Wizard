# APP_QA_PROJECT_AUTOMATION — ProjectAutomationScreen Test Coverage Gaps

## Screen: ProjectAutomationScreen (`automation/ProjectAutomationScreen.kt`)
**Existing tests:** 9 tests in `ProjectAutomationScreenTest.kt`
**Behaviors identified:** 54 | **Gaps:** 41

---

## HIGH Priority — Schedules

### QA-AUTO-1: Create schedule end-to-end
Typing a valid cron into `schedule_cron_input`, clicking `schedule_create_button`, verifying the new item appears and the form closes.

### QA-AUTO-2: Create button disabled when cron invalid
`schedule_create_button` is disabled when cron field is blank or contains an invalid expression.

### QA-AUTO-3: Cron validation error feedback
Typing an invalid cron (e.g., `"bad"`) shows error-colored supporting text and `isError` on the field.

### QA-AUTO-4: Cron preset dropdown
Clicking `schedule_preset_selector` opens the dropdown; selecting a preset populates `schedule_cron_input`.

### QA-AUTO-5: Confirm delete removes schedule
Clicking confirm on delete confirmation causes the schedule item to disappear.

### QA-AUTO-6: Schedule toggle interaction
Clicking `schedule_toggle_s1` fires the toggle; the switch reflects the new state.

---

## HIGH Priority — Webhook Triggers

### QA-AUTO-7: Add webhook button disabled while saving
When `isSaving = true` the `add_webhook_button` is disabled.

### QA-AUTO-8: Webhook secret card appears after creation
After clicking add webhook, `webhook_secret_card` is visible with the secret value.

### QA-AUTO-9: Secret card dismiss hides card
Clicking `webhook_secret_dismiss` makes `webhook_secret_card` disappear.

### QA-AUTO-10: Webhook toggle interaction
Clicking `webhook_toggle_t1` fires the toggle.

### QA-AUTO-11: Confirm webhook delete removes item

---

## HIGH Priority — Maven Triggers

### QA-AUTO-12: Create maven end-to-end
Fill in all required fields, click `maven_create_button`, verify snackbar shows and item appears.

### QA-AUTO-13: Maven create button disabled when fields empty
`maven_create_button` is disabled when required fields are blank.

### QA-AUTO-14: Maven repo URL validation error
Typing a non-http(s) URL shows error text and error styling.

### QA-AUTO-15: Maven toggle interaction
Clicking `maven_toggle_m1` fires the toggle.

### QA-AUTO-16: Confirm maven delete removes item

---

## HIGH Priority — Screen-level

### QA-AUTO-17: Error snackbar on load failure
When HTTP client returns an error, a snackbar error message appears.

### QA-AUTO-18: Error snackbar on create failure
When `createSchedule`/`createWebhookTrigger`/`createMavenTrigger` fails, error snackbar appears.

---

## MEDIUM Priority

### QA-AUTO-19: Cron validation success feedback
Typing a valid but unrecognized cron shows success-colored supporting text.

### QA-AUTO-20: Known-cron hint (next run)
After selecting "Daily" preset, a "next run" hint appears.

### QA-AUTO-21: Schedule form resets on reopen
### QA-AUTO-22: Dismiss schedule form without submitting
### QA-AUTO-23: Cancel schedule delete keeps item
### QA-AUTO-24: Human-readable cron description in item
### QA-AUTO-25: Only one schedule confirmation visible at a time
### QA-AUTO-26: Webhook copy button
### QA-AUTO-27: Webhook item shows URL
### QA-AUTO-28: Cancel webhook delete keeps item
### QA-AUTO-29: Maven create button "Saving..." label
### QA-AUTO-30: Maven form resets on reopen
### QA-AUTO-31: Dismiss maven form without submitting
### QA-AUTO-32: Include snapshots checkbox
### QA-AUTO-33: Cancel maven delete keeps item
### QA-AUTO-34: Maven item "never checked" label
### QA-AUTO-35: Loading spinner while data loads
### QA-AUTO-36: Error snackbar on toggle failure
### QA-AUTO-37: Multiple items per section

---

## LOW Priority

### QA-AUTO-38: Webhook secret not re-shown after dismiss
### QA-AUTO-39: Maven item repo URL shown
### QA-AUTO-40: Maven relative time display
### QA-AUTO-41: Maven delete confirmation includes artifact coordinates
