# APP_QA_CONNECTION_LIST — ConnectionListScreen Test Coverage Gaps

## Screen: ConnectionListScreen (`connections/ConnectionListScreen.kt`)
**Existing tests:** 18 tests in `ConnectionScreensTest.kt`
**Behaviors identified:** 35 | **Gaps:** 20

---

## HIGH Priority

### QA-CONNLIST-1: Sorting actually reorders the displayed list
Tests verify sort label changes but never assert the visual order of items in the list.

### QA-CONNLIST-2: Test button shows spinner while in-progress
The "Test" button disables and replaces its label with a `CircularProgressIndicator` while `isTesting = true`.

### QA-CONNLIST-3: Test success shows snackbar
`viewModel.testConnection` emits a success message that appears as a snackbar. Never verified.

### QA-CONNLIST-4: Test failure shows snackbar with error
When `testConnection` returns `success = false`, the error message is shown in a snackbar.

### QA-CONNLIST-5: Confirm delete actually removes the item from the list
Only dialog appearance is tested. No test clicks "Confirm" and verifies the item disappears.

---

## MEDIUM Priority

### QA-CONNLIST-6: Webhook URL label shown for applicable items
Items with a non-null `webhookUrl` show it with `testTag("webhook_url_${id}")`.

### QA-CONNLIST-7: Type badge shown with correct text
`RwBadge` with `testTag("connection_type_badge_${id}")` renders the type name. Never asserted.

### QA-CONNLIST-8: Empty state "Create connection" button calls onCreateConnection
`empty_state_create_connection_button` is tagged but never clicked.

### QA-CONNLIST-9: No-results empty state (search active, no matches)
The distinct no-results layout with Search icon and "Clear search" button is untested.

### QA-CONNLIST-10: "Clear search" button resets both query and filter
The button calls both `setSearchQuery("")` and `setTypeFilter(null)`.

### QA-CONNLIST-11: Type filter chips function
No test clicks a type chip and verifies only matching connections remain visible.

### QA-CONNLIST-12: Toggling already-selected type chip back to "All"
The chip toggle logic has no test coverage.

### QA-CONNLIST-13: "All" chip deselects active type filter
Not tested.

### QA-CONNLIST-14: Search field filters visible connections
No test types into `search_field` and verifies only matching items are shown.

---

## LOW Priority

### QA-CONNLIST-15: Only one delete confirmation open at a time
### QA-CONNLIST-16: Sort by OLDEST updates label
### QA-CONNLIST-17: Refresh indicator visible during manual refresh
### QA-CONNLIST-18: Retry action in snackbar re-loads data
### QA-CONNLIST-19: Pagination load-more trigger
### QA-CONNLIST-20: Back button absent when onBack is null
