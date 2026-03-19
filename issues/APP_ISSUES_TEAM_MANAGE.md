# TeamManage Issues

**Screen:** TeamManage (`/teams/{id}/manage`)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/TeamManageScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/TeamManageViewModel.kt`

---

## 2J-1: No section dividers between Members/Invites/JoinRequests
- **Severity:** Medium
- **Location:** Lines 257-315
- **Problem:** Only 8dp spacer between sections. No visual separator. Hard to distinguish where one section ends and another begins on long lists.
- **Fix:** Add `HorizontalDivider` between Members and Pending Invites, and between Pending Invites and Join Requests, matching the style after Edit Team (line 189).

## 2J-2: "Promote"/"Demote" labels lack context
- **Severity:** Medium
- **Location:** Lines 413-419
- **Problem:** Single-word labels without indication of target role. Users unfamiliar with TEAM_LEAD/COLLABORATOR roles can't predict the outcome.
- **Fix:** Change to "Promote to Lead" and "Demote to Collaborator".

## 2J-3: No per-action loading states
- **Severity:** Medium
- **Problem:** Save team, remove member, toggle role, cancel invite, approve/reject join request — all lack loading indicators. Only `InviteUserInlineForm` has `isInviting` state with spinner.
- **Fix:** Disable clicked button during API call. Show spinner for save operation.

## 2J-4: No reject confirmation for join requests
- **Severity:** Medium
- **Location:** Lines 456-479, `JoinRequestItem`
- **Problem:** "Reject" fires immediately. Rejecting is potentially destructive (user must re-request). Other destructive actions (member removal, invite cancellation) have confirmations.
- **Fix:** Add `RwInlineConfirmation` for reject action.

## 2J-5: Invite form appears disconnected from trigger button
- **Severity:** Medium
- **Location:** Lines 193-208, 221-228
- **Problem:** `InviteUserInlineForm` appears between edit-team divider and members section, but "Invite User" button is in the members header. Spatially disconnected on long lists.
- **Fix:** Move inline invite form to appear directly below the "Invite User" button. Or auto-scroll to form when it opens.

## 2J-6: Save button scrolls out of view
- **Severity:** Medium
- **Location:** Lines 172-187
- **Problem:** Save button at top of LazyColumn scrolls away when managing members. User may forget unsaved changes.
- **Fix:** Show a persistent "unsaved changes" banner at top/bottom when `hasEditChanges` is true. Or make the edit section a sticky header.

## 2J-7: Pending Invites/Join Requests sections hidden when empty
- **Severity:** Low
- **Location:** Lines 257-291, 294-315
- **Problem:** Sections disappear entirely when their lists are empty. Users can't discover these features exist.
- **Fix:** Always show section headers with count (even "Pending Invites (0)") and a brief empty message.

## 2J-8: Unicode checkmark in snackbar instead of localized string
- **Severity:** Low
- **Location:** Line 177
- **Problem:** `"$editName \u2713"` bypasses localization and may not be accessible.
- **Fix:** Use `packStringResource(Res.string.teams_updated_success)`.

## 2J-9: Inconsistent section heading padding
- **Severity:** Low
- **Location:** Lines 153, 217, 268, 305
- **Problem:** Members heading uses `padding(Spacing.lg)` (16dp all), others use `padding(horizontal = Spacing.lg, vertical = Spacing.sm)`.
- **Fix:** Standardize all to `padding(horizontal = Spacing.lg, vertical = Spacing.sm)`.

## 2J-10: Hardcoded 80dp bottom spacer
- **Severity:** Low
- **Location:** Line 357
- **Problem:** Magic number with no semantic rationale.
- **Fix:** Use `contentPadding = PaddingValues(bottom = 80.dp)` on the LazyColumn.
