# APP_QA_RELEASE_DETAIL — ReleaseDetailScreen Test Coverage Gaps

## Screen: ReleaseDetailScreen (`releases/ReleaseDetailScreen.kt`)
**Existing tests:** 28 tests in `ReleaseScreensTest.kt`
**Behaviors identified:** 36 | **Gaps:** 29

---

## HIGH Priority

### QA-RELDETAIL-1: Stop release flow end-to-end
`stop_release_button` appears for RUNNING releases but no test clicks it, confirms, and verifies `onStopRelease` is called.

### QA-RELDETAIL-2: Resume release button visible for STOPPED release
`resume_release_button` only exists for STOPPED status. No test asserts its presence.

### QA-RELDETAIL-3: Resume release fires callback
No test clicks Resume and verifies `onResumeRelease` is called.

### QA-RELDETAIL-4: Cancel button present for PENDING release
Tested for RUNNING but PENDING is a separate branch.

### QA-RELDETAIL-5: Stop block flow for RUNNING block
`stop_block_button` for a RUNNING block with release RUNNING — click → confirm → `onStopBlock` fires.

### QA-RELDETAIL-6: Stop block button for WAITING_FOR_INPUT block
Separate Stop button shown alongside Approve when release is RUNNING.

### QA-RELDETAIL-7: Rerun callback fires
Button existence is tested but no test clicks it and verifies `onRerun`.

### QA-RELDETAIL-8: Archive callback fires
Button existence is tested but no test clicks it and verifies `onArchive`.

### QA-RELDETAIL-9: Error snackbar when error prop is non-null
Never tested.

---

## MEDIUM Priority

### QA-RELDETAIL-10: Loading state asserts spinner and "Loading..." text
Current test only checks cancel button absence when `release=null`.

### QA-RELDETAIL-11: Stop release confirmation can be dismissed
Dismiss path on `confirm_stop_release`.

### QA-RELDETAIL-12: Cancel confirmation can be dismissed
Dismiss path on `confirm_cancel_release`.

### QA-RELDETAIL-13: Block "waiting" panel (no execution entry)
Block in DAG with no `BlockExecution` should show "Waiting" status and waiting info.

### QA-RELDETAIL-14: Stopped block in detail panel
No test opens panel for a STOPPED block and asserts stopped context text.

### QA-RELDETAIL-15: Stopped block duration label
The STOPPED static label path in `block_duration_text` is untested.

### QA-RELDETAIL-16: Webhook status card visible
When `execution.webhookStatus != null`, `webhook_status_card` should appear.

### QA-RELDETAIL-17: Webhook placeholder text
Webhook enabled but no updates yet — `webhook_status_placeholder` should appear.

### QA-RELDETAIL-18: Sub-builds section renders
No test provides a `BlockExecution` with `subBuilds` populated.

### QA-RELDETAIL-19: Sub-builds expand/collapse
Clicking `sub_builds_header` should toggle `sub_builds_list` visibility.

### QA-RELDETAIL-20: Sub-builds discovering placeholder
RUNNING TeamCity/GitHub Action block with no sub-builds.

### QA-RELDETAIL-21: Error section toggle (collapse/expand)
The section starts expanded; collapse behavior is untested.

---

## LOW Priority

### QA-RELDETAIL-22: Copy to clipboard button in error section
### QA-RELDETAIL-23: Gate phase text (POST and unknown paths)
### QA-RELDETAIL-24: Approval progress counter text content ("1 of 3")
### QA-RELDETAIL-25: Rerun button for FAILED/CANCELLED/STOPPED releases
### QA-RELDETAIL-26: Archive button for FAILED/CANCELLED/STOPPED releases
### QA-RELDETAIL-27: Status badge for non-RUNNING statuses in title
### QA-RELDETAIL-28: Disconnected indicator absent when connected
### QA-RELDETAIL-29: Gate message text content
