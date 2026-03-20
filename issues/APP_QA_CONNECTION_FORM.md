# APP_QA_CONNECTION_FORM — ConnectionFormScreen Test Coverage Gaps

## Screen: ConnectionFormScreen (`connections/ConnectionFormScreen.kt`)
**Existing tests:** 21 tests in `ConnectionScreensTest.kt`
**Behaviors identified:** 30 | **Gaps:** 18

---

## HIGH Priority

### QA-CONNFORM-1: Dirty-state triggers discard confirmation on Back
No test types into any field (making the form dirty), then clicks Back, and verifies the `discard_confirm` inline confirmation appears.

### QA-CONNFORM-2: Discard confirmation "Discard" navigates away
No test clicks the Confirm button and verifies `onBack` is called.

### QA-CONNFORM-3: Discard confirmation "Cancel" keeps user on form
No test verifies dismissing the discard dialog keeps the user on the form.

### QA-CONNFORM-4: Save error shows error banner
When `createConnection` or `updateConnection` fails, `connection_form_error_banner` should appear.

### QA-CONNFORM-5: Error banner dismiss button clears error
`connection_error_dismiss` is tagged but never tested.

---

## MEDIUM Priority

### QA-CONNFORM-6: Save button shows "Saving..." text while isSaving
The button label swaps to "Saving..." during the in-flight save.

### QA-CONNFORM-7: Polling interval rejects non-digit characters
Both TeamCity and GitHub polling interval fields filter non-digit input.

### QA-CONNFORM-8: Polling interval coercion to 5–300 range
Entering "0", "3", or "999" should be clamped via `coerceIn(5, 300)`.

### QA-CONNFORM-9: TeamCity polling interval field exists and has default
`teamcity_polling_interval` is never directly asserted.

### QA-CONNFORM-10: GitHub polling interval field exists and has default
`github_polling_interval` is never asserted.

### QA-CONNFORM-11: Section headers shown for Slack and TeamCity types
Only `section_header_github` is tested.

### QA-CONNFORM-12: Password visibility toggle for Slack webhook URL
Only the GitHub token toggle is tested.

### QA-CONNFORM-13: Password visibility toggle for TeamCity token
`teamcity_token_toggle_visibility` is never tested.

### QA-CONNFORM-14: Edit mode clean form back navigates without confirmation
For edit mode with an unmodified pre-populated form, no test verifies Back navigates directly.

---

## LOW Priority

### QA-CONNFORM-15: Lock icon and hint text visible in edit mode
### QA-CONNFORM-16: Edit mode pre-populated polling intervals
### QA-CONNFORM-17: Switching type clears previous type's field values
### QA-CONNFORM-18: Form is clean on initial load for create mode
