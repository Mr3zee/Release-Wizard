# TeamDetail Issues

**Screen:** TeamDetail (`/teams/{id}`)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/TeamDetailScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/TeamDetailViewModel.kt`

**Note:** This screen has the highest density of critical/high issues.

---

## 2H-1: Missing SnackbarHost [CRITICAL]
- **Severity:** Critical
- **Location:** Scaffold declaration, lines 55-96
- **Problem:** TeamDetailScreen is the **only screen** in the entire app without a `SnackbarHost`. Errors from `leaveTeam` are shown as full-page error state instead of transient snackbar. The `dismissError()` method exists in ViewModel but is **never called** from the screen.
- **Fix:** Add `snackbarHost = { SnackbarHost(snackbarHostState) }` to the Scaffold. Wire up snackbar for transient errors.

## 2H-2: Error state replaces all loaded content [HIGH]
- **Severity:** High
- **Location:** Lines 102-109
- **Problem:** When an error occurs (e.g., `leaveTeam` failure), the entire content area is replaced by error screen — team info and member list disappear. User loses context.
- **Fix:** Use snackbar for transient action errors (leave team failure). Reserve full-page error state only for initial load failures. Separate `loadError` from `actionError` in ViewModel.

## 2H-3: Description truncated to 2 lines on detail view [HIGH]
- **Severity:** High
- **Location:** Lines 125-131
- **Problem:** `maxLines = 2` on the **detail** screen — defeats the purpose of a detail view.
- **Fix:** Remove `maxLines` constraint entirely, or increase to `maxLines = 10`.

## 2H-4: No refresh button/indicator [HIGH]
- **Severity:** High
- **Problem:** No `RefreshIconButton` in TopAppBar, no `LinearProgressIndicator`, no `RefreshErrorBanner`. Only `Cmd+R` keyboard shortcut exists (undiscoverable).
- **Fix:** Add `RefreshIconButton` to TopAppBar actions. Add `LinearProgressIndicator` below TopAppBar during refresh.

## 2H-5: Leave confirmation may appear off-screen [HIGH]
- **Severity:** High
- **Location:** Lines 143-158
- **Problem:** `RwInlineConfirmation` is a LazyColumn item between info card and "Members" heading. If user scrolled down and clicked "Leave", the confirmation appears off-screen.
- **Fix:** Place `RwInlineConfirmation` in a fixed position above the LazyColumn (in the parent Column), not inside it.

## 2H-6: Info card has no heading/anchor
- **Severity:** Medium
- **Location:** Lines 117-141
- **Problem:** Card contains only description + member count in `onSurfaceVariant` — no team name, no visual anchor. Team name only in TopAppBar.
- **Fix:** Add team name as heading inside the card, or add a "Team Info" section header.

## 2H-7: No loading indicator during leave action
- **Severity:** Medium
- **Location:** `TeamDetailViewModel.kt`, lines 52-60
- **Problem:** `leaveTeam` doesn't set `_isLoading`. No visual feedback during API call.
- **Fix:** Set loading state during the leave operation. Disable the Leave button while in progress.

## 2H-8: No tooltips on toolbar buttons
- **Severity:** Medium
- **Location:** Lines 66-92
- **Problem:** Back, Audit Log, Manage, and Leave buttons all lack `RwTooltip` wrappers.
- **Fix:** Wrap each with `RwTooltip`.

## 2H-9: Collaborator badge low contrast
- **Severity:** Medium
- **Location:** Lines 203-206
- **Problem:** `onSurfaceVariant` at `0.15f` alpha for badge background produces extremely low contrast.
- **Fix:** Increase badge background alpha or use a different badge color for Collaborator.

## 2H-10: Warning icon semantically wrong for "leave"
- **Severity:** Low
- **Location:** Line 89
- **Problem:** `Icons.Default.Warning` (triangle with exclamation) for Leave Team. Should be exit/logout.
- **Fix:** Change to `Icons.AutoMirrored.Filled.ExitToApp` or `Icons.Default.Logout`.

## 2H-11: No empty state for members list
- **Severity:** Medium
- **Location:** Lines 172-177
- **Problem:** Empty member list shows nothing — just the heading and empty space.
- **Fix:** Add "No members yet" empty state message.

## 2H-12: No bottom content padding on LazyColumn
- **Severity:** Low
- **Problem:** Last member card renders flush against bottom edge.
- **Fix:** Add `contentPadding = PaddingValues(bottom = Spacing.xl)`.

## 2H-13: Warning icon missing contentDescription
- **Severity:** Medium
- **Location:** Line 89
- **Problem:** `contentDescription = null` on the warning icon in Leave button.
- **Fix:** Add `contentDescription = "Warning"`.
