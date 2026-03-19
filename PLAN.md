# UI/UX Audit Fix Plan

68 findings from comprehensive visual + code audit. Grouped into 10 phases by area and dependency order. Each phase is a self-contained unit of work.

---

## Phase 1: Critical Bugs (Broken Functionality) ✅

**Status:** Complete (commit `1cdaf2a`)

All 6 bugs fixed, reviewed by UX/Design/Compose/QA experts, 11 new UI tests + flaky test fix. Server 354/354, Compose 306/306 pass.

- **1A.** Replaced cron regex with `isValidCron()` supporting ranges/steps/lists
- **1B.** Excluded `/auth/me`, `/auth/register` from 401 session-expired interceptor
- **1C.** Changed invite API to accept username; server resolves via AuthService; inline error in dialog
- **1D.** Added debounced undo push with flush-before-undo semantics
- **1E.** Added loadMore concurrency + hasMore guard with loading spinner
- **1F.** Replaced LazyColumn with Column+verticalScroll in TemplatePickerDialog

---

## Phase 2: Auth & Login UX ✅

**Status:** Complete (commit `6c6ab71`)

Enter key submit, password visibility toggle, server-specific error messages. 6 new tests + 2 fixed. 12 login tests pass.

- **2A.** Enter on username moves focus to password; Enter on password submits form. Auto-focus username on load. Uses `onPreviewKeyEvent` (Desktop pattern).
- **2B.** Password visibility toggle with eye icon in trailing slot. `focusProperties { canFocus = false }` prevents focus stealing. Renamed `connections_show/hide_password` → `common_` prefix across all 6 language packs.
- **2C.** Replaced hardcoded `UiMessage.InvalidCredentials`/`RegistrationFailed` with `toUiMessage()`. Server errors now surface directly (e.g. "Username already taken").

---

## Phase 3: Editor Robustness

**Goal:** Fix editor navigation safety, input handling, and interaction issues.

### 3A. Add unsaved-changes guard on "Automation" navigation
- **File:** `DagEditorScreen.kt:134-143`
- **Fix:** Check `isDirty` before navigating, show discard dialog (same as Back button at line 88-102).

### 3B. Add numeric filter to timeout field
- **File:** `BlockPropertiesPanel.kt:296-313`
- **Fix:** Add `KeyboardOptions(keyboardType = KeyboardType.Number)` and show error for non-numeric input.

### 3C. Add click-to-open for external config selector dropdown
- **File:** `BlockPropertiesPanel.kt:426`
- **Fix:** Add `onClick = { dropdownExpanded = true }` modifier or a dropdown button.

### 3D. Add scroll to validation errors dialog
- **File:** `DagEditorScreen.kt:542-551`
- **Fix:** Add `verticalScroll(rememberScrollState())` to the Column.

### 3E. Scale edge hit testing threshold with zoom level
- **File:** `DagCanvas.kt:62`
- **Fix:** Divide threshold by current zoom scale: `8f / scale`.

---

## Phase 4: Release Monitoring Fixes

**Goal:** Fix release detail display issues and missing interactions.

### 4A. Wire selection highlight into ExecutionDagCanvas
- **File:** `ExecutionDagCanvas.kt:156`, `ReleaseDetailScreen.kt:55`
- **Fix:** Pass `selectedBlockId` to `ExecutionDagCanvas` and set `isSelected = true` for matching block.

### 4B. Show block info when clicking WAITING blocks
- **File:** `ReleaseDetailScreen.kt:336-349`
- **Fix:** When `execution == null`, still show block's static info (name, type, status) in the detail panel.

### 4C. Add Cancel button for PENDING releases
- **File:** `ReleaseDetailScreen.kt:244-296`
- **Fix:** Add a Cancel action for `PENDING` status alongside the existing `RUNNING`/`STOPPED` guards.

### 4D. Navigate to newly created release after starting
- **File:** `ReleaseListViewModel.kt:226-235`
- **Fix:** Return the new release ID from `startRelease()` and invoke `onNavigateToRelease` callback.

### 4E. Fix STOPPED block duration (dead code)
- **File:** `ReleaseDetailScreen.kt:440`
- **Fix:** Remove the dead `finishedAt?.let` branch. For stopped blocks without `finishedAt`, freeze the elapsed time at the stop moment (or show "Stopped" label).

### 4F. Add WAITING_FOR_INPUT duration display
- **File:** `ReleaseDetailScreen.kt:408-448`
- **Fix:** Add branch for `WAITING_FOR_INPUT` showing elapsed wait time.

### 4G. Render SubBuild `buildUrl` as clickable link
- **File:** `SubBuildsSection.kt`
- **Fix:** If `buildUrl` is non-null, render as a clickable link opening the external CI system.

### 4H. Add missing status filter chips (STOPPED, PENDING, CANCELLED, ARCHIVED)
- **File:** `ReleaseListScreen.kt:217`
- **Fix:** Add the remaining status values as filter chips.

### 4I. Fix timestamp formatting inconsistencies
- **Files:** `ReleaseListScreen.kt:387`, `ErrorDetailSection.kt:62`, `SubBuildsSection.kt:153`
- **Fix:** Use `toLocalDateTime()` formatting consistently. Reuse `formatDuration()` for sub-build durations.

### 4J. Initialize elapsed duration with computed value
- **File:** `ReleaseDetailScreen.kt:424`
- **Fix:** Initialize with `formatDuration(Clock.System.now() - startedAt)` instead of empty string.

### 4K. Conditionally activate infinite transition on ExecutionDagCanvas
- **File:** `ExecutionDagCanvas.kt:59-68`
- **Fix:** Only run animation when `hasRunningBlocks` is true.

---

## Phase 5: Connections & Teams Fixes ✅

**Status:** Complete (worktree `phase5-connections-teams`)

All 8 fixes implemented, reviewed by UX/Design/Compose/QA experts, 306/306 Compose tests pass. Manual UI verification completed.

- **5A.** Added per-connection loading indicator (`_testingConnectionIds` Set) + duplicate-call guard
- **5B.** Added unsaved-changes guard on TeamManageScreen (RwInlineConfirmation pinned above LazyColumn)
- **5C.** Self-removal prevention: "(You)" pill badge replaces Promote/Demote/Remove for current user
- **5D.** Non-member team click guard: `ListItemCard(onClick = null)` for non-members
- **5E.** isDirty tracks only selected connection type fields via `when (selectedType)` dispatch
- **5F.** Polling interval hint via `supportingText` when field is blank
- **5G.** "No projects available" empty state in StartReleaseInlineForm
- **5H.** RefreshIconButton + LinearProgressIndicator + RefreshErrorBanner on AuditLog and MyInvites

---

## Phase 6: Automation Screen Consistency Overhaul

**Goal:** Align the entire automation module with the app's component system. This single phase resolves ~15 findings.

### 6A. Replace M3 components with Rw* wrappers
- **Files:** `ProjectAutomationScreen.kt`, `CreateScheduleDialog.kt`, `CreateMavenTriggerDialog.kt`, `CreateWebhookTriggerDialog.kt`, `WebhookSecretDialog.kt`, `MavenTriggerCreatedDialog.kt`
- Replace `Card` -> `RwCard` (3 instances)
- Replace `IconButton` -> `RwIconButton` (4 instances)
- Replace `Checkbox` -> `RwCheckbox` (1 instance)
- Replace `OutlinedTextField` -> `RwTextField` (5+ instances)
- Replace `Switch` -> keep M3 (no `RwSwitch` exists) but ensure consistent styling
- Make checkbox label clickable (`CreateMavenTriggerDialog.kt:80-89`)

### 6B. Replace hardcoded dp with Spacing tokens
- Replace `16.dp` -> `Spacing.lg`, `12.dp` -> `Spacing.md`, `8.dp` -> `Spacing.sm`, `4.dp` -> `Spacing.xs`, `2.dp` -> `Spacing.xxs` across all automation files.

### 6C. Add human-readable cron descriptions to schedule items
- **File:** `ProjectAutomationScreen.kt:279`
- Reuse `nextRunHint` logic from `CreateScheduleDialog` to show descriptions alongside raw cron expressions.

### 6D. Add test tags to all automation dialog fields and buttons
- **Files:** All dialog files in `automation/`
- Add `testTag` modifiers to form fields, confirm/dismiss buttons.

---

## Phase 7: Internationalization Fixes

**Goal:** Replace all hardcoded English strings with string resources. Add language pack overrides.

### 7A. Fix formatRelativeTime in automation screen
- **File:** `ProjectAutomationScreen.kt:450-458`
- **Fix:** Use `packStringResource`/`packPluralStringResource` (same pattern as `ReleaseListScreen:96-112`).

### 7B. Localize cron preset hints
- **File:** `CreateScheduleDialog.kt:40-43`
- **Fix:** Replace hardcoded "Every day at 9:00 AM" etc. with string resources.

### 7C. Localize Maven trigger validation error
- **File:** `CreateMavenTriggerDialog.kt:51`
- **Fix:** Replace `"Must start with http:// or https://"` with string resource.

### 7D. Localize default block names
- **File:** `EditorToolbar.kt:77, 167-172`
- **Fix:** Pass string resource keys into the block creation functions, or make `defaultBlockName()` composable.

### 7E. Localize icon contentDescriptions in BlockPropertiesPanel
- **File:** `BlockPropertiesPanel.kt:359, 508`
- **Fix:** Replace hardcoded "Refresh parameters"/"Refresh configurations" with string resources.

### 7F. Localize audit event action names
- **File:** `AuditLogScreen.kt:125`
- **Fix:** Map action enum values to string resources instead of `name.replace("_", " ")`.

**Note:** Per CLAUDE.md, every new string resource must have themed overrides in all 6 language packs.

---

## Phase 8: Keyboard Shortcuts Architecture Fix

**Goal:** Fix the shortcut propagation system and add shortcuts to missing screens.

### 8A. Fix shortcut actions propagation from child screens to global handler
- **File:** `App.kt:148-157`
- **Problem:** `SideEffect` at parent level reads parent's `LocalShortcutActions`, never sees child screen overrides.
- **Fix:** Have screens directly set `shortcutActionsState.value` via a callback or `LaunchedEffect` instead of relying on CompositionLocal propagation.

### 8B. Add ShortcutActions to missing screens
- **Files:** `TeamDetailScreen`, `TeamManageScreen`, `MyInvitesScreen`, `AuditLogScreen`, `ProjectAutomationScreen`, `ReleaseDetailScreen`
- **Fix:** Provide appropriate `ShortcutActions` (F5 for refresh, Ctrl+S for save where applicable).

---

## Phase 9: Minor UX Polish

**Goal:** Fix remaining usability papercuts.

### 9A. Fix pagination inconsistencies
- Convert AuditLog "Load More" button to auto-pagination (`loadMoreItem()` pattern)
- Add pagination to TeamListScreen (currently hardcoded limit=50)

### 9B. Fix typography hierarchy in team member lists
- **Files:** `TeamDetailScreen.kt:193`, `TeamManageScreen.kt:319`
- **Fix:** Change member username from `AppTypography.heading` to `AppTypography.subheading`.

### 9C. Fix ConnectionFormScreen error display
- **File:** `ConnectionFormScreen.kt:377-386`
- **Fix:** Use banner/snackbar pattern instead of inline text at bottom.

### 9D. Fix RwTextField modifier placement
- **File:** `RwTextField.kt:80, 103`
- **Fix:** Apply `modifier` to outer `Column` instead of inner `BasicTextField`.

### 9E. Fix ArtifactTreeView icon padding order
- **File:** `ArtifactTreeView.kt:129`
- **Fix:** Change `Modifier.size(16.dp).padding(...)` to `Modifier.padding(...).size(16.dp)`.

---

## Phase 10: Cosmetic Consistency

**Goal:** Align spacing, styling, and component usage across the app.

- Replace remaining hardcoded `dp` values with `Spacing` tokens (`LoadMoreItem.kt:30`, `KeyboardShortcutsOverlay.kt:202-216`, `TeamManageScreen.kt:227`)
- Use `ListItemCard` consistently for invite cards and audit event items
- Add horizontal scroll to status filter row (`ReleaseListScreen.kt:206`)
- Add tooltip/copy mechanism for truncated release IDs (`ReleaseListScreen.kt:373`)
- Replace unicode triangles with Material icons for gate expand/collapse (`BlockPropertiesPanel.kt:530`)
- Fix lock-lost dialog button placement (`DagEditorScreen.kt:358-370`)
- Add missing `testTag` modifiers (CreateProject confirm button, "All" status chip, ReleaseDetail back button)
- Fix `RefreshErrorBanner` dismiss icon tint for error container contrast
- Standardize `contentPadding` in toolbar buttons (replace `6.dp` with `Spacing.xs`)

---

## Execution Order

Phases are ordered by impact and dependency:
1. **Phase 1** first — broken features
2. **Phase 2-5** in parallel — independent screen areas
3. **Phase 6** — automation overhaul (biggest single batch)
4. **Phase 7** — i18n (depends on Phase 6 component changes)
5. **Phase 8** — shortcuts (architectural fix)
6. **Phase 9-10** — polish (lowest priority)

Each phase follows the dev cycle: implement -> review -> fix -> review -> manual UI verify -> write tests -> update docs.
