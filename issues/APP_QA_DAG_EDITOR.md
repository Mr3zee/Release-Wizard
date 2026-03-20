# APP_QA_DAG_EDITOR — DagEditorScreen Test Coverage Gaps

## Screen: DagEditorScreen (`editor/DagEditorScreen.kt`)
**Existing tests:** ~40 tests across `DagEditorScreenTest.kt` + `DagCanvasTest.kt`
**Behaviors identified:** 86 | **Gaps:** 60

---

## HIGH Priority

### QA-EDITOR-1: Back button navigates immediately when graph is clean
No test verifies `onBack` is actually called when the graph is not dirty.

### QA-EDITOR-2: Back button triggers discard_confirm banner when dirty
The banner appearance after modifying the graph and clicking back is untested.

### QA-EDITOR-3: Confirming discard in discard_confirm banner calls onBack
The full confirm → navigate flow is untested.

### QA-EDITOR-4: Dismissing discard_confirm banner keeps user on screen
The cancel path of the discard confirmation is untested.

### QA-EDITOR-5: Lock-lost confirmation banner appears and functions
`lock_lost_confirm` banner for LockLost + dirty state, including "Reacquire & Save" extra action.

### QA-EDITOR-6: Back button blocked while isSaving is true
No test simulates an in-flight save and verifies back button is blocked.

### QA-EDITOR-7: Ctrl/Cmd+S triggers save when dirty (positive path)
Only tested as no-op in read-only; never tested that it works when writable.

### QA-EDITOR-8: Validation error badge appears when errors exist
No test creates a validation error (e.g., self-loop) and verifies the badge appears.

### QA-EDITOR-9: Clicking validation badge opens error dropdown
The dropdown listing validation errors is untested.

### QA-EDITOR-10: Force-unlock confirm → calls viewModel.forceUnlock()
Only appearance is tested, not the confirm action effect.

### QA-EDITOR-11: Lock banner Retry button calls retryAcquireLock()
The retry button interaction is untested.

### QA-EDITOR-12: Lock-lost banner shown with "Reacquire & Save" button
The `lock_lost_banner` state and its action button are untested.

### QA-EDITOR-13: Delete/Backspace key removes selected block via keyboard
The keyboard shortcut path (not toolbar button) is untested.

### QA-EDITOR-14: Delete/Backspace key removes selected edge via keyboard
Same gap as above for edges.

### QA-EDITOR-15: Ctrl+C copies selected blocks (paste button becomes enabled)
Copy interaction via keyboard is untested.

### QA-EDITOR-16: Ctrl+V pastes clipboard blocks onto canvas
Paste interaction via keyboard is untested.

### QA-EDITOR-17: Ctrl+A selects all blocks
Select-all interaction via keyboard is untested.

### QA-EDITOR-18: Changing block type via dropdown marks graph dirty
The `block_type_selector` dropdown interaction is untested.

### QA-EDITOR-19: Remove parameter button removes corresponding row
Only add parameter is tested; remove is not.

### QA-EDITOR-20: Properties panel fields disabled in read-only mode
Toolbar buttons are tested as disabled in read-only; properties panel fields are not.

### QA-EDITOR-21: Transient error (save failure) shown in snackbar
Error snackbar after project is loaded is untested.

### QA-EDITOR-22: Canvas read-only: dragging does NOT move blocks
Canvas in read-only mode drag behavior is untested.

### QA-EDITOR-23: Canvas read-only: port drag does NOT create edges
Canvas in read-only mode edge creation is untested.

---

## MEDIUM Priority

### QA-EDITOR-24: Dirty indicator in top bar title
`*` or `(modified)` text appears when `isDirty = true` and not read-only.

### QA-EDITOR-25: Save button text changes to "Saving..." while saving
The transitional button label is untested.

### QA-EDITOR-26: Ctrl+Shift+Z triggers redo
The redo keyboard shortcut is untested.

### QA-EDITOR-27: Automation button shown/hidden based on onOpenAutomation
Conditional rendering of the automation button is untested.

### QA-EDITOR-28: Automation button triggers discard confirmation when dirty
Navigation guard for automation button is untested.

### QA-EDITOR-29: Delete/Backspace suppressed while confirmation banner visible
The guard logic for keyboard shortcuts during confirmations is untested.

### QA-EDITOR-30: Force-unlock confirm dismiss closes without calling forceUnlock
The cancel path of force-unlock confirmation is untested.

### QA-EDITOR-31: Lock-error variant (red styling) when info == null
Network error on lock acquisition styling is untested.

### QA-EDITOR-32: Sidebar collapse/expand (left)
`toggle_left_sidebar` button and toolbar visibility toggling.

### QA-EDITOR-33: Sidebar collapse/expand (right)
`toggle_right_sidebar` button and properties panel visibility toggling.

### QA-EDITOR-34: Timeout field accepts only digits
Non-digit input filtering is untested.

### QA-EDITOR-35: Timeout field required-error state
Error state when block type requires timeout and field is blank.

### QA-EDITOR-36: Inject webhook URL checkbox visibility
Visible for `TEAMCITY_BUILD` blocks; absent for other types.

### QA-EDITOR-37: Connection selector visible for applicable block types
`block_connection_selector` rendering logic is untested.

### QA-EDITOR-38: Gate section toggle expands/collapses
`gate_section_toggle` interaction is untested.

### QA-EDITOR-39: Pre/post gate checkboxes enable gate fields
Gate checkbox interaction is untested.

### QA-EDITOR-40: Gate approval count validation (< 1)
Error state for invalid approval count is untested.

### QA-EDITOR-41: Refresh configs button triggers re-fetch
`refresh_configs_button` interaction is untested.

### QA-EDITOR-42: Config selector loading/error states
Config selector spinner during fetch and error display are untested.

### QA-EDITOR-43: Canvas read-only: clicking block still selects it
Selection should be allowed in read-only for viewing properties.

### QA-EDITOR-44: Validation error type formatting
Each `ValidationError` type formats as the correct string.

---

## LOW Priority

### QA-EDITOR-45: Dirty indicator absent when clean
### QA-EDITOR-46: Validation badge absent when no errors
### QA-EDITOR-47: Connection selector placeholder text
### QA-EDITOR-48: Gate count badge in section header
### QA-EDITOR-49: Template picker dropdown
### QA-EDITOR-50: Snackbar dismiss action label
### QA-EDITOR-51: Error cleared from ViewModel after snackbar shown
