# APP_QA_TEAM_DETAIL — TeamDetailScreen Test Coverage Gaps

## Screen: TeamDetailScreen (`teams/TeamDetailScreen.kt`)
**Existing tests:** 2 tests in `TeamScreensTest.kt`
**Behaviors identified:** 21 | **Gaps:** 14

---

## HIGH Priority

### QA-TEAMDETAIL-1: Initial load failure renders full-page error state
Error text and Retry button should appear when the load fails.

### QA-TEAMDETAIL-2: Retry button calls loadDetail()
The retry interaction is untested.

### QA-TEAMDETAIL-3: Manage button NOT visible when isTeamLead = false
Only the positive case (visible when lead) is tested. The negative case is not.

### QA-TEAMDETAIL-4: Leave Team button shows inline confirmation
`RwInlineConfirmation` with team name in message should appear on click.

### QA-TEAMDETAIL-5: Confirming leave fires onBack() callback
The leave → navigate flow is untested.

### QA-TEAMDETAIL-6: Cancelling leave hides confirmation
The cancel path is untested.

---

## MEDIUM Priority

### QA-TEAMDETAIL-7: "Audit Log" button fires onAuditLog()
The audit log navigation callback is untested.

### QA-TEAMDETAIL-8: "Leave Team" button disabled while isLeaving
The disabled state during in-flight leave is untested.

### QA-TEAMDETAIL-9: Empty members list shows empty state
Icon + "No members yet" text when members list is empty.

### QA-TEAMDETAIL-10: Refresh error shows banner; dismiss hides it
### QA-TEAMDETAIL-11: Action error shows snackbar
Leave failure should surface as a snackbar error.

---

## LOW Priority

### QA-TEAMDETAIL-12: Description absent when blank
### QA-TEAMDETAIL-13: Back button fires onBack()
### QA-TEAMDETAIL-14: Role badge color distinction (Lead vs Collaborator)
