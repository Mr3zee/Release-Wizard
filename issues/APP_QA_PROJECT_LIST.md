# APP_QA_PROJECT_LIST — ProjectListScreen Test Coverage Gaps

## Screen: ProjectListScreen (`projects/ProjectListScreen.kt`)
**Existing tests:** 19 tests in `ProjectListScreenTest.kt`
**Behaviors identified:** 35 | **Covered:** ~19 | **Gaps:** 18

---

## HIGH Priority

### QA-PROJLIST-1: Sorting actually reorders the displayed list
Tests verify the sort dropdown label changes, but no test asserts the rendered order of project items changes. A bug in the `sortedProjects` `remember` block would go undetected.

### QA-PROJLIST-2: Search field filters the list
No test types a query into `search_field` and verifies that only matching projects appear (or the "no search results" state). The search debounce path is entirely untested at the UI layer.

### QA-PROJLIST-3: "No search results" empty state
The branch `searchQuery.isNotBlank() && projects.isEmpty()` shows a different empty state with a "Clear search" button. Never exercised.

### QA-PROJLIST-4: "Clear search" button clears the query and restores the list
The button's behavior (`viewModel.setSearchQuery("")`) has no test.

### QA-PROJLIST-5: Delete confirmation — confirm path actually deletes the project
The existing test only covers the cancel path. No test clicks Confirm, waits for the DELETE API call, and asserts the project disappears.

### QA-PROJLIST-6: Initial loading spinner is shown
No test asserts that `CircularProgressIndicator` appears during the initial load.

---

## MEDIUM Priority

### QA-PROJLIST-7: "Create project" button in empty state opens the form
The `empty_state_create_project_button` is tagged but never clicked.

### QA-PROJLIST-8: Create form — dismiss/cancel closes form without creating
No test clicks the cancel/dismiss action on the create form.

### QA-PROJLIST-9: Create form — "Create" button disabled when name is blank
The `enabled = name.isBlank()` guard is never tested.

### QA-PROJLIST-10: Create form resets on re-open
After closing and reopening the form, the name field should be empty (`LaunchedEffect(visible)` resets it).

### QA-PROJLIST-11: Delete confirmation — only one confirmation visible at a time
When the user opens delete menu for a second project while a confirmation for the first is showing, the first should disappear.

### QA-PROJLIST-12: Pagination / "Load more" behavior
`loadMoreItem` and `viewModel.loadMore()` are never exercised.

### QA-PROJLIST-13: `LinearProgressIndicator` visibility during refresh
The `refresh_indicator` test tag exists but is never asserted.

### QA-PROJLIST-14: Project description conditionally rendered
No test explicitly asserts that the blank-description project does NOT show a description line.

### QA-PROJLIST-15: Error state Retry button actually triggers a reload
The test only asserts the Retry button exists. No test clicks it and verifies `viewModel.loadProjects()` is called.

---

## LOW Priority

### QA-PROJLIST-16: Keyboard shortcut suppression when dialog is open
The `hasDialogOpen` flag disables search/create/refresh shortcuts.

### QA-PROJLIST-17: Duplicate refresh_button test
`refresh button exists` and `refresh icon button exists in top bar` are identical tests.

### QA-PROJLIST-18: Manual vs background refresh opacity distinction
The `LinearProgressIndicator` renders at different alpha values. Not testable via semantics.
