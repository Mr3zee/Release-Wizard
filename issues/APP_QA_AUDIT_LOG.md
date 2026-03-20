# APP_QA_AUDIT_LOG — AuditLogScreen Test Coverage Gaps

## Screen: AuditLogScreen (`teams/AuditLogScreen.kt`)
**Existing tests:** 0 tests — COMPLETELY UNTESTED
**Behaviors identified:** 12 | **Gaps:** 10

---

## HIGH Priority

### QA-AUDIT-1: Screen renders with audit_log_screen testTag
The most basic rendering test is missing.

### QA-AUDIT-2: Events list rendered with correct content
Action name, badge, actor, timestamp should be visible after load.

### QA-AUDIT-3: Empty state shown when no events
Icon + "No audit events yet." + hint text.

### QA-AUDIT-4: Initial load error shows snackbar with Retry
Error handling path is completely untested.

---

## MEDIUM Priority

### QA-AUDIT-5: Refresh button exists and triggers re-fetch
### QA-AUDIT-6: Refresh error shows banner; dismiss hides it
### QA-AUDIT-7: Event details text shown when non-blank; omitted when blank
### QA-AUDIT-8: Target type badge rendered per event
### QA-AUDIT-9: Pagination load-more trigger

---

## LOW Priority

### QA-AUDIT-10: Back button fires onBack()
