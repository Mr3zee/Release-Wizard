# AuditLog Issues

**Screen:** AuditLog (`/teams/{id}/audit`)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/AuditLogScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/teams/AuditLogViewModel.kt`

---

## 2L-1: No filtering or search capability [MEDIUM-HIGH]
- **Severity:** Medium-High
- **Location:** Entire screen
- **Problem:** No search field or filter controls, unlike every other list screen (TeamList has search, ReleaseList has search + status/project filters, ConnectionList has search + type filters). With 40+ action types across 9 target categories, finding specific events is difficult.
- **Fix:** Add:
  1. Search/text filter (matching actor username, action name, or details)
  2. Target type filter chips (Team, User, Project, Release, etc.)

## 2L-2: Not using ListItemCard component
- **Severity:** Medium
- **Location:** Lines 154-204, `AuditEventItem`
- **Problem:** Manually constructs `RwCard > Column > Row` layout instead of using shared `ListItemCard`. The Row at line 166 lacks `verticalAlignment = Alignment.CenterVertically`.
- **Fix:** Refactor to use `ListItemCard` for consistent styling, or add `verticalAlignment` to the Row.

## 2L-3: No timestamp grouping or date separators
- **Severity:** Medium
- **Problem:** All events in a flat list. No "Today", "Yesterday", "March 18" headers. Users must read individual timestamps.
- **Fix:** Group events by date. Add sticky headers or visual separators per day.

## 2L-4: Missing contentPadding on LazyColumn
- **Severity:** Medium
- **Location:** Line 135
- **Problem:** Every other list screen uses `contentPadding = PaddingValues(bottom = 80.dp)`. Last event card sits flush against bottom.
- **Fix:** Add `contentPadding = PaddingValues(bottom = Spacing.xl)`.

## 2L-5: Badge color doesn't vary by target type
- **Severity:** Low-Medium
- **Location:** Lines 177-181
- **Problem:** All target type badges use `MaterialTheme.colorScheme.primary`. With 9 target types, everything looks the same.
- **Fix:** Assign distinct colors per target type or category group.

## 2L-6: Details text truncated to 2 lines with no expand
- **Severity:** Low-Medium
- **Location:** Lines 198-199
- **Problem:** `maxLines = 2, TextOverflow.Ellipsis` with no click-to-expand. Important details may be hidden.
- **Fix:** Add click-to-expand toggle or "show more" text.

## 2L-7: Duplicated date formatting
- **Severity:** Low
- **Location:** Line 186
- **Problem:** Manual formatting instead of using shared `formatTimestamp()` utility in `StringResources.kt`.
- **Fix:** Replace with `formatTimestamp(event.timestamp)`.

## 2L-8: Snackbar uses Dismiss instead of Retry
- **Severity:** Low
- **Problem:** Error snackbar offers "Dismiss" but no retry. Other screens offer "Retry" and call `loadX()`.
- **Fix:** Change to Retry action that calls `viewModel.loadEvents()`.

## 2L-9: TopAppBar title generic
- **Severity:** Low
- **Location:** Line 74
- **Problem:** Just "Audit Log" — no team name context.
- **Fix:** Show "Audit Log — {Team Name}".

## 2L-10: Empty state lacks explanatory text
- **Severity:** Low
- **Location:** Lines 118-133
- **Problem:** "No audit events yet." with no guidance. Events are auto-generated, not user-created.
- **Fix:** Add "Audit events will appear here as team activity occurs."
