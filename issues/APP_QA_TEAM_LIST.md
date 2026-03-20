# APP_QA_TEAM_LIST — TeamListScreen Test Coverage Gaps

## Screen: TeamListScreen (`teams/TeamListScreen.kt`)
**Existing tests:** 9 tests in `TeamScreensTest.kt`
**Behaviors identified:** 24 | **Gaps:** 16

---

## HIGH Priority

### QA-TEAMLIST-1: Non-member team item shows "Request to Join" button
Non-member teams render a "Request to Join" button instead of member badge; item is non-clickable and alpha-dimmed.

### QA-TEAMLIST-2: Member team item click fires onTeamClick callback
Clicking a member team item should navigate with the correct `TeamId`.

### QA-TEAMLIST-3: memberTeamIds drives member vs non-member rendering
The `memberTeamIds` set controls which items are interactive vs dimmed.

### QA-TEAMLIST-4: "Request to Join" calls VM, success shows info banner
The request-to-join flow and resulting inline info message banner are untested.

### QA-TEAMLIST-5: Empty search results state
When search query is non-blank and list is empty, the search-empty state with "Clear Search" button is shown.

### QA-TEAMLIST-6: Create form submit button disabled when name blank
The form's `enabled` guard is never tested.

### QA-TEAMLIST-7: Create form submit calls onTeamCreated with new TeamId
Submitting the create form and its callback are untested.

### QA-TEAMLIST-8: Initial load failure shows snackbar with Retry
The error snackbar with "Retry" action is untested.

---

## MEDIUM Priority

### QA-TEAMLIST-9: Info message banner shown and dismissed
The inline banner after join-request success can be dismissed.

### QA-TEAMLIST-10: "Clear Search" button resets query
Clears search and causes the list to re-render.

### QA-TEAMLIST-11: Dismissing create form without creating
Cancel closes the form without side effects.

### QA-TEAMLIST-12: Create form resets fields on reopen
Name and description fields should be empty on subsequent opens.

### QA-TEAMLIST-13: Back button absent when onBack is null; present when provided
### QA-TEAMLIST-14: Pagination load-more trigger

---

## LOW Priority

### QA-TEAMLIST-15: Initial loading spinner
### QA-TEAMLIST-16: Team description hidden when blank
