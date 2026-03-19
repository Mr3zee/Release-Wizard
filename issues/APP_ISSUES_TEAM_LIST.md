# TeamList Issues

**Screen:** TeamList (`/teams`, Alt+4)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/TeamListScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/TeamListViewModel.kt`

---

## 2I-1: Non-member cards look clickable but aren't
- **Severity:** Medium
- **Location:** `TeamListItem`, when `isMember = false`, `onClick = null`
- **Problem:** Card is visually identical to clickable cards. Only the "Request to Join" ghost button hints at non-interactivity. Users may attempt to click the card body.
- **Fix:** Add visual distinction: reduced opacity (e.g., `alpha(0.8f)`), a different card variant, or a small lock icon for non-member teams.

## 2I-2: "Request to Join" lacks loading/disabled state
- **Severity:** Medium
- **Location:** `TeamListViewModel.requestToJoin()`
- **Problem:** Button not disabled during network call or after submission. User can click multiple times. No per-card loading indicator.
- **Fix:** Track pending join requests per team ID. Disable button while request is in-flight. Change to "Requested" state after success.

## 2I-3: Create team navigates away immediately
- **Severity:** Medium
- **Location:** `onTeamCreated(teamId)` callback
- **Problem:** After creating a team, user is navigated to the new team immediately without warning. If they wanted to create multiple teams, they must navigate back.
- **Fix:** Stay on the Teams list after creation. Show success snackbar with "Go to team" action.

## 2I-4: "My Invites" ghost button lacks visual affordance
- **Severity:** Low
- **Location:** TopAppBar action
- **Problem:** Plain blue text with no border, no icon. Users may miss it.
- **Fix:** Add a mail/envelope icon next to the text. When pending invites exist, show badge count (e.g., "My Invites (2)").
