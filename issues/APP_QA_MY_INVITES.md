# APP_QA_MY_INVITES — MyInvitesScreen Test Coverage Gaps

## Screen: MyInvitesScreen (`teams/MyInvitesScreen.kt`)
**Existing tests:** 3 tests in `TeamScreensTest.kt`
**Behaviors identified:** 16 | **Gaps:** 10

---

## HIGH Priority

### QA-INVITES-1: Accept triggers acceptInvite, fires callback, removes invite
Clicking "Accept" should trigger `acceptInvite`, fire `onInviteAccepted()`, and remove the invite from the list.

### QA-INVITES-2: Decline opens inline confirmation with team name
Clicking "Decline" should show an inline confirmation containing the team name.

### QA-INVITES-3: Confirming decline removes invite from list
The confirm path of the decline confirmation is untested.

### QA-INVITES-4: Initial load error shows snackbar with Retry
The error snackbar and retry action are untested.

---

## MEDIUM Priority

### QA-INVITES-5: Accept button shows spinner while loading
The loading indicator and disabled state for the specific invite being accepted.

### QA-INVITES-6: Decline button disabled while invite is loading
Both buttons should be disabled while an accept is in-flight.

### QA-INVITES-7: Refresh button exists and triggers re-fetch
### QA-INVITES-8: Refresh error shows banner; dismiss hides it

---

## LOW Priority

### QA-INVITES-9: Back button fires onBack()
### QA-INVITES-10: Initial loading spinner
