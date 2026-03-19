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

## Phase 3: Editor Robustness ✅

**Status:** Complete

Reviewed by UX/Design/Compose/QA experts, 306/306 Compose tests pass.

- **3A.** Replaced boolean dialog flags with nullable navigation lambdas; extracted `guardedNavigate` helper checking `isDirty`/`lockState`/`isSaving`; both Back and Automation buttons route through it
- **3B.** Added `text.filter { it.isDigit() }` to timeout field and gate approval count field
- **3C.** Added `FocusInteraction.Focus` collection via `interactionSource` to open config selector dropdown on focus
- **3D.** Already done (scroll + heightIn already present) — skipped
- **3E.** Added `zoom` parameter to `hitTest()`; port and edge thresholds scale inversely with zoom, clamped via `coerceIn` to prevent extremes

---

## Phase 4: Release Monitoring Fixes ✅

**Status:** Complete (commit `f948abf`)

All 11 items fixed, reviewed by UX/Design/Compose/QA experts, 311/311 Compose + 354/354 Server tests pass, UI verified.

- **4A.** Wired `selectedBlockId` into ExecutionDagCanvas — blocks and connected edges highlight on selection; clicking empty canvas space deselects
- **4B.** Changed guard to `block != null` — blocks without execution data now show minimal waiting panel with name, type, status
- **4C.** Added Cancel button for PENDING releases using existing confirmation flow
- **4D.** `startRelease()` now accepts `onCreated` callback — navigates to new release detail after successful API response
- **4E.** Replaced dead-code STOPPED duration branch with static "Stopped" label (no `stoppedAt` field in model)
- **4F.** Added WAITING_FOR_INPUT live ticker showing "Waiting: Xm Ys"
- **4G.** Added clickable "Open" link for sub-builds with `buildUrl` using `LocalUriHandler`
- **4H.** Added all 7 status filter chips + horizontal scroll on chip row
- **4I.** Extracted `formatTimestamp`/`formatTime`/`formatDuration` utilities; replaced ISO 8601 `Instant.toString()` everywhere
- **4J.** Initialized RUNNING elapsed with computed value instead of empty string
- **4K.** Wrapped `hasRunningBlocks` in `remember(blockExecutions)` to avoid redundant recomputation

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
