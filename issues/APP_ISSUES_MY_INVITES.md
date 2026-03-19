# MyInvites Issues

**Screen:** MyInvites (`/teams/my-invites`)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/MyInvitesScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/MyInvitesViewModel.kt`

---

## 2K-1: Invite cards don't fill available width [HIGH]
- **Severity:** High
- **Location:** Lines 138-151
- **Problem:** Cards shrink-wrap to text content instead of filling the LazyColumn width. `RwCard` has `fillMaxWidth()` but inside a `LazyColumn` with `horizontalAlignment = CenterHorizontally`, the card needs explicit width from its parent.
- **Fix:** Add `fillParentMaxWidth()` modifier to LazyColumn items, or wrap items in a modifier that forces full width.

## 2K-2: Card layout (vertical Column) inconsistent with ListItemCard [HIGH]
- **Severity:** High
- **Location:** `InviteCard` composable
- **Problem:** Every other list screen uses `ListItemCard` (horizontal Row with content left, actions right). InviteCard uses a vertical Column with stacked content + buttons. Looks fundamentally different.
- **Fix:** Refactor `InviteCard` to use `ListItemCard` or replicate its horizontal Row layout. Place team name + "Invited by" on left, Accept/Decline buttons on right.

## 2K-3: No decline confirmation
- **Severity:** Medium
- **Problem:** "Decline" fires immediately. Irreversible — team admin must re-send invite.
- **Fix:** Add `RwInlineConfirmation` for decline action. Message: "Decline invite from {team}? You'll need a new invite to join."

## 2K-4: No per-card loading state during accept/decline
- **Severity:** Medium
- **Problem:** No visual indication that accept/decline is in progress. Could lead to double-clicks.
- **Fix:** Track loading state per invite ID. Disable both buttons and show spinner during API call.

## 2K-5: No invite count badge on "My Invites" button
- **Severity:** Medium
- **Location:** TeamListScreen TopAppBar
- **Problem:** No indication of pending invite count. Users don't know they have invites without navigating.
- **Fix:** Add `RwBadge` with pending invite count on the "My Invites" button. The `RwBadge` component already exists.

## 2K-6: Decline button uses same blue as Accept
- **Severity:** Low
- **Problem:** Both Accept (Primary, filled blue) and Decline (Ghost, blue text) use blue. Decline is destructive/negative but uses same color.
- **Fix:** Use `onSurfaceVariant` for Decline button text color to visually de-emphasize it.

## 2K-7: No animation on card removal
- **Severity:** Low
- **Problem:** Card disappears instantly when invite is declined/accepted. No transition.
- **Fix:** Add `animateItem()` modifier to LazyColumn items for smooth fade/slide removal.
