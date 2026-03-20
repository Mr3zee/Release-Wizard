# APP_QA_RELEASE_LIST — ReleaseListScreen Test Coverage Gaps

## Screen: ReleaseListScreen (`releases/ReleaseListScreen.kt`)
**Existing tests:** 15 tests in `ReleaseScreensTest.kt`
**Behaviors identified:** 25 | **Gaps:** 21

---

## HIGH Priority

### QA-RELLIST-1: Status filter chip interaction filters the list
No test clicks a chip (e.g., FAILED) and verifies the displayed list changes. The chip selected state is never asserted.

### QA-RELLIST-2: Status filter chip toggle (deselect)
Clicking an already-selected chip should revert to "All". The toggle logic is untested.

### QA-RELLIST-3: Search field interaction filters the list
No test types into the search field and asserts the list is filtered.

### QA-RELLIST-4: Empty state with active filter shows "Clear filters"
When search or filter yields 0 results, the screen shows a different empty state with "Clear filters" button.

### QA-RELLIST-5: "Clear filters" button clears search and all filters
The button should clear search, status filter, and project filter simultaneously.

### QA-RELLIST-6: Archive flow end-to-end
No test clicks MoreVert → Archive → inline confirmation → confirm.

### QA-RELLIST-7: Delete flow end-to-end
No test exercises the delete path through menu → confirmation → confirm.

---

## MEDIUM Priority

### QA-RELLIST-8: Archive menu item hidden for ARCHIVED releases
The source guard is untested.

### QA-RELLIST-9: MoreVert menu only for terminal-status releases
RUNNING/PENDING releases must not show the menu.

### QA-RELLIST-10: StartReleaseInlineForm — "no projects" message
When `projects` is empty, the form should show a message and disable Start.

### QA-RELLIST-11: StartReleaseInlineForm — project selection enables Start
`start_release_confirm` is disabled until a project is selected.

### QA-RELLIST-12: StartReleaseInlineForm — submit calls startRelease
No test confirms the form closes after clicking Start.

### QA-RELLIST-13: StartReleaseInlineForm — dismiss/cancel closes form
### QA-RELLIST-14: Project filter chips render and are interactive
### QA-RELLIST-15: Project filter chip toggle
### QA-RELLIST-16: Empty state "Start Release" button opens form

---

## LOW Priority

### QA-RELLIST-17: Back button absent when onBack is null
### QA-RELLIST-18: "Last updated" ticker text content changes over time
### QA-RELLIST-19: Pagination load-more
### QA-RELLIST-20: Retry button triggers reload
### QA-RELLIST-21: STOPPED status badge
