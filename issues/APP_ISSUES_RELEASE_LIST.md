# ReleaseList Issues

**Screen:** ReleaseList (`/releases`, Alt+2)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/releases/ReleaseListScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/releases/ReleaseListViewModel.kt`

---

## 2D-1: Release items display raw UUID instead of project name [HIGH]
- **Severity:** High
- **Location:** Lines 566-574
- **Problem:** Each release card shows "Project: cdc429db" (first 8 chars of `projectTemplateId` UUID). The `Release` model carries only the UUID, not the project name. The ViewModel fetches `ProjectTemplate` objects for filters but doesn't pass the mapping to `ReleaseListItem`.
- **Fix:** Build a `Map<ProjectId, String>` from `viewModel.projects` and pass it to `ReleaseListItem` to resolve the name. Alternatively, enrich the `Release` model with `projectName` from the server.

## 2D-2: Release title shows raw UUID fragment [HIGH]
- **Severity:** High
- **Location:** Line 560
- **Problem:** "Release 7554f325" — just the first 8 hex digits of the release UUID. Multiple releases from the same project are nearly indistinguishable.
- **Fix:** Show project name + timestamp (e.g., "MyPipeline — Mar 19, 14:32"), or a sequential number ("Release #3"), or user-provided tags.

## 2D-3: Terminology mismatch: "Create Release" vs "Start release"
- **Severity:** Low
- **Location:** String resources — `releases_create_release` = "Create Release", `releases_start_release` = "Start release", `start_release_title` = "Start Release"
- **Problem:** Mixed terminology and casing confuse users about whether these are different actions.
- **Fix:** Unify to "Start Release" (title case) everywhere.

## 2D-4: Error state visually sparse
- **Severity:** Medium
- **Location:** Lines 328-347
- **Problem:** Error state shows only red text + Retry button centered vertically. No icon. Compare with empty state (48dp icon + text + CTA).
- **Fix:** Add an error icon (e.g., `Icons.Outlined.ErrorOutline` at 48dp), match the empty state's visual weight.

## 2D-5: Start Release form missing placeholder in project dropdown
- **Severity:** Medium
- **Location:** Lines 503-516
- **Problem:** Empty text field with only a dropdown arrow — no placeholder "Select a project". Users may not realize they need to select.
- **Fix:** Add `placeholder = "Select a project"` to the dropdown field.

## 2D-6: "Updated just now" ticker unique to this screen
- **Severity:** Medium
- **Location:** Lines 96-125, 143-150
- **Problem:** Only ReleaseList has the "Updated X ago" subtitle in TopAppBar. Other list screens don't. Inconsistent mental model.
- **Fix:** Either add the ticker to all list screens or remove from this one.

## 2D-7: Status filter vs project filter inconsistent vertical spacing
- **Severity:** Medium
- **Location:** Lines 246-248 vs 289-291
- **Problem:** "Status" label has `padding(top = Spacing.sm)` (8dp), "Project" label has `padding(top = Spacing.xs)` (4dp).
- **Fix:** Standardize both to `padding(top = Spacing.sm)`.

## 2D-8: "Archived" status badge nearly invisible in dark mode
- **Severity:** Low
- **Location:** Lines 622-646, `AppColors.kt` lines 283-284
- **Problem:** `statusArchived = Color(0xFF4B5563)` at 15% alpha on dark surface is barely visible.
- **Fix:** Increase alpha to 0.25f for archived/pending badges, or use a minimum contrast floor.

## 2D-9: Release tooltip shows raw UUID
- **Severity:** Low
- **Location:** Line 558 (`RwTooltip(tooltip = release.id.value)`)
- **Problem:** Tooltip shows full UUID on hover — not user-friendly.
- **Fix:** Show "Release ID: {uuid}" in monospace, or add a "Copy ID" affordance.
