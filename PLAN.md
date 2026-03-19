# UX Audit Fix Plan

83 issues found across 13 screens (2 Critical, 15 Major, 33 Minor, 33 Cosmetic).
Organized into 3 phases by dependency. Within each phase, all steps run in parallel.

---

## Phase 1 — Component Infrastructure ✅

Foundation changes that later phases depend on. All 3 steps are independent.

### Step 1A: Add `leadingIcon` to `RwTextField` ✅

- Added `leadingIcon: @Composable (() -> Unit)? = null` parameter
- Renders before text area with `Spacing.sm` gap, respects `isError` state

### Step 1B: Add `RwDangerZone` component ✅

- Created `RwDangerZone` composable with error-tinted background/border and "Danger Zone" label
- Added `common_danger_zone` string resource + all 6 language pack overrides

### Step 1C: Add `RwBadge` component ✅

- Created `RwBadge(text, color, modifier, testTag)` — pill-shaped `Surface` with 15% alpha tinted background + colored text
- Replaced ad-hoc badge in Teams List ("Member") and Team Manage ("You") with `RwBadge`
- 8 UI tests added in `Phase1ComponentsTest.kt`

---

## Phase 2 — Screen Fixes (all steps in parallel) ✅

Every step targets an independent screen/area. No dependencies between steps.

### Step 2A: Login Screen (5 issues)

| # | Sev | Fix |
|---|-----|-----|
| 1 | Major | Add "Confirm Password" field in Register mode with match validation |
| 2 | Major | Show password requirements as `supportingText` below password field in Register mode |
| 3 | Major | Normalize focus ring on mode-switch button — ensure consistent Ghost styling in both modes |
| 4 | Minor | Reserve fixed `heightIn(min = 20.dp)` for error message area to prevent layout shift |
| 5 | Minor | Bump error text from `bodySmall` to `body` for better readability |

Skip: logo (cosmetic, product decision), forgot password (feature, not a fix).

### Step 2B: Project List Screen (6 issues)

| # | Sev | Fix |
|---|-----|-----|
| 1 | Major | Move "Sign Out" from nav bar into the overflow menu (already has Theme, Language, Shortcuts) |
| 2 | Major | Replace inline "Delete" text button with overflow menu (MoreVert + DropdownMenu) — match Release List pattern |
| 3 | Major | Add active state indicator to nav bar — highlight current section with bottom border or distinct text color |
| 4 | Minor | Add `leadingIcon` search icon to search bar (uses Phase 1A) |
| 5 | Minor | Add trailing chevron-right icon on project cards to signal navigability |
| 6 | Minor | Increase team-switcher dropdown arrow from 20dp to 24dp |

### Step 2C: DAG Editor (10 issues)

| # | Sev | Fix |
|---|-----|-----|
| 1 | Critical | Increase edge stroke width to 2.5dp, brighten edge color in dark mode to `Color(0xFFBFC5D0)`, replace 3.5dp dot with a proper triangle arrowhead (8dp) |
| 2 | Critical | Add empty-canvas hint: "Drag from an output port to an input port to connect blocks" when edges list is empty |
| 3 | Major | Increase `PORT_RADIUS` from 5f to 8f, brighten default port color to `Color(0xFFB0B8C4)` with a 1dp ring outline |
| 4 | Major | Add color swatch (8dp circle) to each palette button matching the block fill color |
| 5 | Major | Add zoom controls overlay in bottom-right of canvas: zoom percentage label, +/- buttons, "Fit" button |
| 6 | Major | Add subtle flow-direction arrow watermark or arrowheads on edges |
| 7 | Minor | Make sidebars collapsible — add toggle buttons on sidebar edges. Left default 200dp, right collapse when no block selected |
| 8 | Minor | Add keyboard shortcut hints as tooltips on all action buttons (Undo → "Ctrl+Z") |
| 9 | Minor | Add shortcut tooltip to Save button; switch to Primary variant when `isDirty` |
| 10 | Minor | Brighten grid: minor stroke to 0.5f + `Color(0xFF353B4E)`, major stroke to 1.0f + `Color(0xFF424960)` |

### Step 2D: Automation Screen (6 issues)

| # | Sev | Fix |
|---|-----|-----|
| 1 | Major | Enrich empty states: add one-sentence description per trigger type + inline "Get started" prompt |
| 2 | Major | Increase inter-section spacing from `Spacing.sm` (8dp) to `Spacing.xl` (20dp) |
| 3 | Minor | Add page description subtitle: "Configure triggers that automatically start releases for this project" |
| 4 | Minor | Add leading icons to section headers: clock for Schedules, link for Webhooks, package for Maven |
| 5 | Minor | Upgrade section headers from `subheading` to `heading` |
| 6 | Minor | Add loading state to webhook create button (parity with Schedule/Maven inline forms) |

### Step 2E: Release List Screen (6 issues)

| # | Sev | Fix |
|---|-----|-----|
| 1 | Major | Change parent Column from `CenterHorizontally` to `Start` alignment; only center the empty state and spinner explicitly |
| 2 | Minor | Add subtle section labels ("Status" / "Project") above each chip row |
| 3 | Minor | Move empty state from vertical center to upper-third with `TopCenter` + top padding |
| 4 | Minor | Add inline "Create Release" button below empty state text |
| 5 | Minor | Add `leadingIcon` search icon to search bar (uses Phase 1A) |
| 6 | Cosmetic | Increase `chipBgSelected` brightness for stronger selected/unselected distinction |

### Step 2F: Connection Screens (7 issues)

| # | Sev | Fix |
|---|-----|-----|
| 1 | Major | Replace raw M3 `OutlinedTextField` for Type dropdown with a custom composable using `RwTextField` + trailing dropdown arrow + attached `DropdownMenu` |
| 2 | Major | Add lock icon + helper text "Type cannot be changed after creation" when `isEditMode` |
| 3 | Major | Style "Delete" on connection cards with `RwButtonVariant.Danger` or add a warning icon to distinguish from "Test" |
| 4 | Minor | Add `Arrangement.spacedBy(Spacing.sm)` between Test and Delete buttons |
| 5 | Minor | Add `leadingIcon` search icon to search bar (uses Phase 1A) |
| 6 | Minor | Add type-specific icon (GitHub/Slack/TeamCity) to connection cards using `RwBadge` (uses Phase 1C) |
| 7 | Minor | Show valid range in Polling Interval supporting text: "5–300 seconds, default: 30" |

### Step 2G: Teams Screens (10 issues)

| # | Sev | Fix |
|---|-----|-----|
| 1 | Major | Wrap "Delete Team" in `RwDangerZone` (uses Phase 1B) |
| 2 | Major | Separate "Leave" from navigation actions — move to rightmost position with spacing/divider, add warning icon |
| 3 | Major | Remove redundant team name from info card on Detail — keep only description + member count, or fold into subtitle |
| 4 | Minor | Use `RwBadge` for member roles — distinct styling for "Lead" vs "Collaborator" (uses Phase 1C) |
| 5 | Minor | Change Team Manage Save button from Ghost to Primary variant |
| 6 | Minor | Add snackbar feedback after successful team edits |
| 7 | Minor | Add `leadingIcon` search icon to teams search bar (uses Phase 1A) |
| 8 | Minor | Add team name subtitle to Manage and Audit Log top bars for navigation context |
| 9 | Minor | Enrich My Invites empty state with icon and guidance text |
| 10 | Minor | Use `RwBadge` for Audit Log category tags (uses Phase 1C) |

---

## Phase 3 — Cross-Cutting Polish (all steps in parallel) ✅

Applied after screen-specific fixes. All steps independent.

### Step 3A: FAB Tooltips & Labels

Wrap all `RwFab` instances in `RwTooltip` across: Project List ("New Project"), Release List ("New Release"), Connection List ("New Connection"), Teams List ("New Team").

### Step 3B: Icon Button Tooltips

Add `RwTooltip` to all icon-only buttons that lack one: refresh buttons, overflow menu buttons, password visibility toggles.

### Step 3C: Dark Theme Contrast Tuning

- Brighten `chromeBorder` from `0xFF3A3F50` to `0xFF4A5060` for better card border visibility
- Verify "Sign In" button text meets WCAG AA 4.5:1 — darken `buttonPrimaryBg` to `0xFF2563EB` or increase text weight
- Verify subtitle text (`onSurfaceVariant`) contrast on dark surfaces

### Step 3D: Empty State Consistency Pass

Review all empty states after Phase 2 changes and ensure consistent pattern:
- Icon above message text
- Descriptive message
- Inline CTA button (where applicable, in addition to FAB)

---

## Scope Notes

**Deferred (product decisions, not UX bugs):**
- App logo/icon on login screen
- Forgot password feature
- Locale-aware timestamp formatting (requires i18n infrastructure)
- Sidebar navigation redesign (architectural change)
- DAG auto-connect on block add (behavior change)

**Already tracked in `project_future_improvements.md`:**
- Check for overlap before adding any issue to that file
