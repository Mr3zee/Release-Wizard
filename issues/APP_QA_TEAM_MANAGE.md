# APP_QA_TEAM_MANAGE — TeamManageScreen Test Coverage Gaps

## Screen: TeamManageScreen (`teams/TeamManageScreen.kt`)
**Existing tests:** 5 tests in `TeamScreensTest.kt`
**Behaviors identified:** 31 | **Gaps:** 21

---

## HIGH Priority

### QA-MANAGE-1: Save button disabled when no changes
The form's dirty-state detection for the save button is untested.

### QA-MANAGE-2: Save button enabled after name or description change
The positive dirty-state case is untested.

### QA-MANAGE-3: Save button disabled when name is blank
Clearing the name field should disable save.

### QA-MANAGE-4: Save calls updateTeam and shows success snackbar
The full save flow is untested.

### QA-MANAGE-5: Back with unsaved changes shows discard confirmation
The discard confirmation banner is untested.

### QA-MANAGE-6: Confirming discard navigates back without saving
The confirm path of the discard dialog is untested.

### QA-MANAGE-7: Invite form submit disabled when username blank
The invite form's `enabled` guard is untested.

### QA-MANAGE-8: Invite error displays inline below input
The error text and `isError` field state are untested.

### QA-MANAGE-9: Invite form auto-closes on success
The dismiss-on-success flow is untested.

### QA-MANAGE-10: Current user shows "You" badge (no toggle/remove)
The "You" badge rendering and button absence for current user are untested.

### QA-MANAGE-11: Remove member — confirmation → confirm removes member
The full remove member flow is untested.

### QA-MANAGE-12: Revoke invite — confirmation → confirm cancels invite
The full revoke invite flow is untested.

### QA-MANAGE-13: Delete Team — confirmation → confirm calls onTeamDeleted()
The full delete team flow is untested.

---

## MEDIUM Priority

### QA-MANAGE-14: Back without changes navigates directly
The clean back-navigation path is untested.

### QA-MANAGE-15: Invite form spinner while isInviting
The loading state on the invite button is untested.

### QA-MANAGE-16: Typing in username clears invite error
The error-clear-on-type behavior is untested.

### QA-MANAGE-17: Invite form dismissed without inviting
The cancel path of the invite form is untested.

### QA-MANAGE-18: Remove member — cancel hides confirmation
### QA-MANAGE-19: API action errors surface as snackbar

---

## LOW Priority

### QA-MANAGE-20: Initial loading spinner
### QA-MANAGE-21: Invite form reset to empty on reopen
