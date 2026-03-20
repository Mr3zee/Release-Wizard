# APP_QA_PLAN — Compose UI Test Coverage Gap Analysis

> Generated 2026-03-20 by 8 parallel QA expert agents analyzing every screen against existing `composeApp/src/jvmTest/` tests.

## Summary

| Screen | Existing Tests | Behaviors | Gaps | HIGH | MEDIUM | LOW |
|--------|---------------|-----------|------|------|--------|-----|
| [LoginScreen](#loginscreen) | 12 | 38 | 16 | 5 | 8 | 3 |
| [ProjectListScreen](#projectlistscreen) | 19 | 35 | 18 | 6 | 9 | 3 |
| [DagEditorScreen](#dageditorscreen) | ~40 | 86 | 51 | 23 | 21 | 7 |
| [ConnectionListScreen](#connectionlistscreen) | 18 | 35 | 20 | 5 | 9 | 6 |
| [ConnectionFormScreen](#connectionformscreen) | 21 | 30 | 18 | 5 | 9 | 4 |
| [TeamListScreen](#teamlistscreen) | 9 | 24 | 16 | 8 | 6 | 2 |
| [TeamDetailScreen](#teamdetailscreen) | 2 | 21 | 14 | 6 | 5 | 3 |
| [TeamManageScreen](#teammanagescreen) | 5 | 31 | 21 | 13 | 6 | 2 |
| [MyInvitesScreen](#myinvitesscreen) | 3 | 16 | 10 | 4 | 4 | 2 |
| [AuditLogScreen](#auditlogscreen) | **0** | 12 | 10 | 4 | 5 | 1 |
| [ReleaseListScreen](#releaselistscreen) | 15 | 25 | 21 | 7 | 9 | 5 |
| [ReleaseDetailScreen](#releasedetailscreen) | 28 | 36 | 29 | 9 | 12 | 8 |
| [ProjectAutomationScreen](#projectautomationscreen) | 9 | 54 | 41 | 18 | 19 | 4 |
| [Cross-Cutting](#cross-cutting) | — | — | 16 | 5 | 7 | 4 |
| **TOTAL** | **~181** | **~443** | **301** | **118** | **129** | **54** |

---

## Critical Patterns Missing Across Multiple Screens

These gap categories repeat across many screens:

1. **Confirm-path of destructive actions** — delete, archive, leave, remove. Most screens test that the confirmation dialog _appears_ but never test the _confirm_ path that executes the action.
2. **Search/filter actually changes the rendered list** — search fields and filter chips exist but no test types/clicks and verifies the list content changes.
3. **Sort order verification** — sort dropdown label changes are tested; actual item reorder is not.
4. **Form validation enforcement** — disabled-button states for blank/invalid input are rarely tested.
5. **Error banner/snackbar on failure** — happy-path rendering is tested; error state rendering is not.
6. **Pagination (load-more)** — not tested on any screen.
7. **Discard-confirmation on back with dirty state** — untested on DagEditor, ConnectionForm, TeamManage.

---

## Per-Screen Issue Files

### LoginScreen
**File:** [issues/APP_QA_LOGIN.md](issues/APP_QA_LOGIN.md)
**Key gaps:** Register mode validation (button disabled states, password mismatch error), loading spinner, keyboard Enter-key flows.

### ProjectListScreen
**File:** [issues/APP_QA_PROJECT_LIST.md](issues/APP_QA_PROJECT_LIST.md)
**Key gaps:** Search filtering, sort reorder verification, delete confirm path, initial loading spinner, no-results empty state.

### DagEditorScreen
**File:** [issues/APP_QA_DAG_EDITOR.md](issues/APP_QA_DAG_EDITOR.md)
**Key gaps:** All navigation guards (back-with-dirty, discard confirm, lock-lost confirm), keyboard shortcuts positive paths (Ctrl+S save, Ctrl+C/V copy/paste, Delete key), validation error badge, properties panel advanced fields (gates, connections, configs), canvas read-only guards.

### ConnectionListScreen
**File:** [issues/APP_QA_CONNECTION_LIST.md](issues/APP_QA_CONNECTION_LIST.md)
**Key gaps:** Test connection snackbar (success/failure), confirm delete removes item, sort reorder, type filter chips, search filtering.

### ConnectionFormScreen
**File:** [issues/APP_QA_CONNECTION_FORM.md](issues/APP_QA_CONNECTION_FORM.md)
**Key gaps:** Dirty-state discard confirmation (all 3 paths), save error banner, polling interval validation.

### TeamListScreen
**File:** [issues/APP_QA_TEAM_LIST.md](issues/APP_QA_TEAM_LIST.md)
**Key gaps:** Member vs non-member rendering, "Request to Join" flow, create form validation, search-empty state, initial load error.

### TeamDetailScreen
**File:** [issues/APP_QA_TEAM_DETAIL.md](issues/APP_QA_TEAM_DETAIL.md)
**Key gaps:** Leave team flow (confirmation + callback), manage button hidden for non-leads, error state with retry.

### TeamManageScreen
**File:** [issues/APP_QA_TEAM_MANAGE.md](issues/APP_QA_TEAM_MANAGE.md)
**Key gaps:** Save dirty-state logic, discard confirmation, invite form validation/error/auto-close, remove member flow, revoke invite flow, delete team flow, "You" badge for current user.

### MyInvitesScreen
**File:** [issues/APP_QA_MY_INVITES.md](issues/APP_QA_MY_INVITES.md)
**Key gaps:** Accept invite flow (callback + removal), decline confirmation flow, load error with retry.

### AuditLogScreen
**File:** [issues/APP_QA_AUDIT_LOG.md](issues/APP_QA_AUDIT_LOG.md)
**Key gaps:** **Completely untested.** Needs basic render, empty state, error/retry, and event content tests.

### ReleaseListScreen
**File:** [issues/APP_QA_RELEASE_LIST.md](issues/APP_QA_RELEASE_LIST.md)
**Key gaps:** Status/project filter chip interaction, search filtering, archive/delete end-to-end flows, StartRelease form validation.

### ReleaseDetailScreen
**File:** [issues/APP_QA_RELEASE_DETAIL.md](issues/APP_QA_RELEASE_DETAIL.md)
**Key gaps:** Stop/Resume/Rerun/Archive callback verification (buttons exist but clicks not tested), stop-block flow, error snackbar, webhook/sub-builds sections, waiting/stopped block states.

### ProjectAutomationScreen
**File:** [issues/APP_QA_PROJECT_AUTOMATION.md](issues/APP_QA_PROJECT_AUTOMATION.md)
**Key gaps:** All three trigger types lack create end-to-end, toggle, and confirm-delete tests. Cron validation, webhook secret card lifecycle, maven URL validation all untested. Error snackbar paths untested.

### Cross-Cutting
**File:** [issues/APP_QA_CROSS_CUTTING.md](issues/APP_QA_CROSS_CUTTING.md)
**Key gaps:** Sidebar nav items never clicked (section navigation bypassed), AppShell collapse/sign-out, session expiry → login redirect, team switcher, RwInlineConfirmation debounce, settings panel, shared component coverage (RwSwitch, RwChip, RwRadioButton).

---

## Recommended Execution Order

Priority is based on: (1) screen complexity, (2) number of HIGH gaps, (3) zero-coverage screens first.

| Phase | Screen(s) | Rationale |
|-------|-----------|-----------|
| 1 | AuditLogScreen | Zero tests — fast win, establishes baseline |
| 2 | TeamManageScreen | 13 HIGH gaps — most destructive untested flows |
| 3 | DagEditorScreen | 23 HIGH gaps — most complex screen, navigation guards critical |
| 4 | ProjectAutomationScreen | 18 HIGH gaps — all 3 trigger CRUD flows untested |
| 5 | ReleaseDetailScreen | 9 HIGH gaps — action callbacks untested |
| 6 | ReleaseListScreen | 7 HIGH gaps — filter/search/archive/delete |
| 7 | TeamListScreen + TeamDetailScreen | Combined 14 HIGH gaps |
| 8 | ConnectionListScreen + ConnectionFormScreen | Combined 10 HIGH gaps |
| 9 | MyInvitesScreen | 4 HIGH gaps — smaller screen |
| 10 | LoginScreen | 5 HIGH gaps — register mode validation |
| 11 | ProjectListScreen | 6 HIGH gaps — search/sort/delete confirm |
| 12 | Cross-Cutting | 5 HIGH gaps — sidebar, session, team switcher |
