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

## Phase 6: Automation Screen Consistency Overhaul ✅

**Status:** Complete (commit `29cb84d`)

All items fixed, reviewed by UX/Design/Compose/Tech Writer/QA experts, 311/311 Compose tests pass.

- **6A.** Replaced `Card` → `RwCard` (3 item composables), `IconButton` → `RwIconButton` (4 instances), `Checkbox` → `RwCheckbox` with clickable label row + `Role.Checkbox` accessibility, `OutlinedTextField` → `RwTextField` (6 of 7; preset selector kept for `menuAnchor` compatibility), `Switch` kept as M3 (no `RwSwitch` exists), `MaterialTheme.typography.bodySmall` → `AppTypography.bodySmall`
- **6B.** No-op — only hardcoded dp was `Modifier.size(16.dp)` for an icon size, not spacing
- **6C.** Extracted `cronDescription()` function; schedule list items now show human-readable descriptions for preset cron expressions
- **6D.** Added testTags: `schedule_preset_selector`, `schedule_description_${id}`, `maven_repo_url_field`, `maven_group_id_field`, `maven_artifact_id_field`, `maven_parameter_key_field`, `maven_include_snapshots`

---

## Phase 7: Internationalization Fixes ✅

**Status:** Complete

All items fixed (7D dropped by expert review), reviewed by UX/Design/Compose/Tech Writer/QA experts, 311/311 Compose tests pass, UI verified across English/GenZ/Helldivers packs.

- **7A.** Converted `formatRelativeTime` to `@Composable` with `packPluralStringResource` for "just now"/"X minutes ago"/"X hours ago"/"X days ago"
- **7B.** Converted `cronDescription` to `@Composable`, reuses existing `schedule_preset_*` keys
- **7C.** Replaced hardcoded Maven URL validation error with `packStringResource(Res.string.maven_url_validation_error)`
- **7D.** DROPPED — Expert consensus: default block names persist to DB, should stay English
- **7E.** Replaced hardcoded "Refresh parameters"/"Refresh configurations" contentDescriptions with string resources
- **7F.** Added `AuditAction.displayName()` (42 values) and `AuditTargetType.displayName()` (9 values) composable extensions; overflow guard on target type Text

55 new string resources + 3 new plural resources, all with themed overrides in 6 language packs.

---

## Phase 8: Keyboard Shortcuts Architecture Fix ✅

**Status:** Complete

311/311 Compose tests pass. Reviewed by UX/Design/Compose/QA experts.

- **8A.** Fixed broken CompositionLocal propagation: replaced parent-level `SideEffect` (self-referential no-op) with callback-based `ProvideShortcutActions` + `LocalShortcutActionsSetter`. Migrated 6 existing screens.
- **8B.** Added `ProvideShortcutActions` to 6 missing screens: TeamDetailScreen (F5), TeamManageScreen (Ctrl+S, F5), MyInvitesScreen (F5), AuditLogScreen (F5), ProjectAutomationScreen (F5), ReleaseDetailScreen (dialog suppression only, WebSocket live data)

---

## Phase 9: Minor UX Polish ✅

**Status:** Complete

311/311 Compose tests pass. Reviewed by UX/Design/Compose/QA experts.

- **9A.** Converted AuditLog manual "Load More" button to auto-pagination (`loadMoreItem()` pattern with `PaginationInfo`). Added pagination to TeamListScreen (limit=50, `loadMore()` for >50 teams).
- **9B.** Changed `AppTypography.heading` → `subheading` for member/invite/request usernames in TeamDetailScreen and TeamManageScreen (4 composables)
- **9C.** Replaced plain error Text in ConnectionFormScreen with `RwCard`-based error banner (`errorContainer` background, dismiss button with `onErrorContainer` tint)
- **9D.** SKIPPED — Moving `modifier` from BasicTextField to outer Column breaks `focusRequester` on 4+ screens
- **9E.** Fixed ArtifactTreeView icon padding order: `size().padding()` → `padding().size()` (2 instances)

---

## Phase 10: Cosmetic Consistency ✅

**Status:** Complete

311/311 Compose tests pass. Reviewed by UX/Design/Compose/QA experts.

- Replaced `16.dp` → `Spacing.lg` in LoadMoreItem, `6.dp` → `Spacing.sm` in EditorToolbar (5 buttons)
- Added tooltip/click-to-copy for truncated release IDs and project template IDs (RwTooltip + copyToClipboard)
- Replaced unicode triangles (▾/▸) with Material Icons (KeyboardArrowDown/Right, 16.dp) in BlockPropertiesPanel gate toggle
- Added missing testTags: `filter_ALL` (ReleaseListScreen), `back_button` (ReleaseDetailScreen), `load_more_item` (LoadMoreItem)
- Fixed RefreshErrorBanner dismiss icon: added explicit `tint = onErrorContainer` for contrast
- SKIPPED: ListItemCard for audit/invite items (layout mismatch), horizontal scroll (already done), lock-lost dialog (already done), KeyboardShortcutsOverlay decorative values (not layout spacing)

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
