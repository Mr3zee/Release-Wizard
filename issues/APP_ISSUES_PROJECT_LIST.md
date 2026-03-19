# ProjectList Issues

**Screen:** ProjectList (`/projects`, Alt+1)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/projects/ProjectListScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/projects/ProjectListViewModel.kt`

---

## 2M-1: DropdownMenu with single "Delete" item
- **Severity:** Medium
- **Location:** Lines 304-323
- **Problem:** Three-dot menu on each project card contains only "Delete". A dropdown with a single entry is a UX anti-pattern — extra click for no benefit. Compare with ConnectionList where delete is inline.
- **Fix:** Either (a) add more menu items (Duplicate, Rename, Export) to justify the dropdown, or (b) show delete action directly in card row.

## 2M-2: No SnackbarHost
- **Severity:** Medium
- **Location:** Scaffold declaration
- **Problem:** No `snackbarHost` parameter. ConnectionListScreen has one. Errors show as full-page state.
- **Fix:** Add `snackbarHost = { SnackbarHost(snackbarHostState) }` to Scaffold.

## 2M-3: Missing "Updated X ago" timestamp
- **Severity:** Medium
- **Problem:** ReleaseListScreen has "Updated X ago" subtitle. ProjectListScreen does not. Inconsistency.
- **Fix:** Either add to ProjectList or remove from ReleaseList. Recommend adding to all list screens for consistency.

## 2M-4: No description field in create project form
- **Severity:** Medium
- **Location:** Lines 334-377, `CreateProjectInlineForm`
- **Problem:** Only a name field. `ProjectTemplate` model has `description` but can't be set during creation.
- **Fix:** Add an optional description text field below the name field.

## 2M-5: Three-dot menu lacks tooltip
- **Severity:** Low
- **Location:** Lines 305-310
- **Problem:** `RwIconButton(Icons.Default.MoreVert)` without `RwTooltip`. Refresh button and FAB have tooltips.
- **Fix:** Wrap in `RwTooltip(tooltip = "More options")`.

## 2M-6: Error state has no dismiss mechanism
- **Severity:** Low
- **Location:** Lines 172-187
- **Problem:** Error text + retry button, but no way to dismiss error and return to empty state.
- **Fix:** Add dismiss button, or auto-clear error after successful retry.
