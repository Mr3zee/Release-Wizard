# APP_ISSUES_PLAN.md — Comprehensive UX/Design Fix Plan

Full audit of all 13 screens produced **224 issues** (15 critical/high, 76 medium, 133 low).
This plan groups issues into **parallel work streams** by affected area. Within each phase, all streams can execute concurrently.

## Detailed Issue Files

Each group has a dedicated file with full descriptions, file locations, and fix instructions:

| File                                                                                 | Scope                                                                         |
|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [`issues/APP_ISSUES_FOUNDATION.md`](issues/APP_ISSUES_FOUNDATION.md)                 | Cross-cutting: theme, contrast, shared components                             |
| [`issues/APP_ISSUES_LOGIN.md`](issues/APP_ISSUES_LOGIN.md)                           | LoginScreen (7 issues)                                                        |
| [`issues/APP_ISSUES_PROJECT_EDITOR.md`](issues/APP_ISSUES_PROJECT_EDITOR.md)         | ProjectEditor (12 issues)                                                     |
| [`issues/APP_ISSUES_PROJECT_AUTOMATION.md`](issues/APP_ISSUES_PROJECT_AUTOMATION.md) | ProjectAutomation (10 issues)                                                 |
| [`issues/APP_ISSUES_RELEASE_LIST.md`](issues/APP_ISSUES_RELEASE_LIST.md)             | ReleaseList (9 issues)                                                        |
| [`issues/APP_ISSUES_RELEASE_VIEW.md`](issues/APP_ISSUES_RELEASE_VIEW.md)             | ReleaseView (25 issues)                                                       |
| [`issues/APP_ISSUES_CONNECTION_LIST.md`](issues/APP_ISSUES_CONNECTION_LIST.md)       | ConnectionList (5 issues)                                                     |
| [`issues/APP_ISSUES_CONNECTION_FORM.md`](issues/APP_ISSUES_CONNECTION_FORM.md)       | ConnectionForm (8 issues)                                                     |
| [`issues/APP_ISSUES_TEAM_DETAIL.md`](issues/APP_ISSUES_TEAM_DETAIL.md)               | TeamDetail (13 issues)                                                        |
| [`issues/APP_ISSUES_TEAM_LIST.md`](issues/APP_ISSUES_TEAM_LIST.md)                   | TeamList (4 issues)                                                           |
| [`issues/APP_ISSUES_TEAM_MANAGE.md`](issues/APP_ISSUES_TEAM_MANAGE.md)               | TeamManage (10 issues)                                                        |
| [`issues/APP_ISSUES_MY_INVITES.md`](issues/APP_ISSUES_MY_INVITES.md)                 | MyInvites (7 issues)                                                          |
| [`issues/APP_ISSUES_AUDIT_LOG.md`](issues/APP_ISSUES_AUDIT_LOG.md)                   | AuditLog (10 issues)                                                          |
| [`issues/APP_ISSUES_PROJECT_LIST.md`](issues/APP_ISSUES_PROJECT_LIST.md)             | ProjectList (6 issues)                                                        |
| [`issues/APP_ISSUES_POLISH.md`](issues/APP_ISSUES_POLISH.md)                         | Low-priority polish (animations, tooltips, spacing, typography, info density) |

---

## Phase 1: Cross-Cutting Foundation (all screens benefit)

These fixes touch shared components or theme tokens — do them first so per-screen work builds on top.
> **Details:** [`issues/APP_ISSUES_FOUNDATION.md`](issues/APP_ISSUES_FOUNDATION.md)

### Stream 1A: Theme & Contrast Fixes
**Files:** `AppColors.kt`, `AppTypography.kt`, `Spacing.kt`

| ID   | Issue                                                                                                    | Severity | Screens Affected                                   |
|------|----------------------------------------------------------------------------------------------------------|----------|----------------------------------------------------|
| 1A-1 | Dark theme `inputPlaceholder` (#7B8494) fails WCAG AA (3.7:1 on #151820) — lighten to ~#9CA3AF           | Medium   | ConnectionForm, LoginScreen, all text fields       |
| 1A-2 | Empty state icons use `onSurfaceVariant.copy(alpha = 0.5f)` — too faint, increase to 0.7f                | Low      | ProjectList, ConnectionList, ReleaseList, TeamList |
| 1A-3 | `onSurfaceVariant` overused for multiple distinct content types — add semantic color tokens where needed | Medium   | ReleaseView, AuditLog                              |

### Stream 1B: RwButton Disabled State
**Files:** `RwButton.kt`

| ID   | Issue                                                                                                                                         | Severity | Screens Affected                       |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------|----------|----------------------------------------|
| 1B-1 | Disabled button uses only `alpha(0.6f)` — insufficient visual distinction. Add desaturated background + dim text for disabled primary buttons | Medium   | LoginScreen, ConnectionForm, all forms |

### Stream 1C: RwInlineForm Enter-to-Submit
**Files:** `RwInlineForm.kt`

| ID   | Issue                                                                | Severity | Screens Affected                   |
|------|----------------------------------------------------------------------|----------|------------------------------------|
| 1C-1 | No Enter key handler to submit inline forms — only Escape is handled | Medium   | TeamList, ProjectList, ReleaseList |

### Stream 1D: "No Search Results" Empty State Pattern
**Files:** `ProjectListScreen.kt`, `ConnectionListScreen.kt`, `TeamListScreen.kt`, `ReleaseListScreen.kt`

| ID   | Issue                                                                                                    | Severity | Screens Affected                                   |
|------|----------------------------------------------------------------------------------------------------------|----------|----------------------------------------------------|
| 1D-1 | "No search results" states lack a decorative icon — inconsistent with main empty states which have icons | Low      | ProjectList, ConnectionList, TeamList, ReleaseList |

### Stream 1E: ListItemCard Consistency
**Files:** `ListItemCard.kt`

| ID   | Issue                                                                                    | Severity | Screens Affected |
|------|------------------------------------------------------------------------------------------|----------|------------------|
| 1E-1 | Audit: verify `ListItemCard` provides at least 44dp touch targets for all action buttons | Low      | All list screens |

---

## Phase 2: Per-Screen Fixes — High/Critical Priority (parallel streams)

### Stream 2A: LoginScreen
> **Details:** [`issues/APP_ISSUES_LOGIN.md`](issues/APP_ISSUES_LOGIN.md)
**Files:** `LoginScreen.kt`, `AuthViewModel.kt`

| ID   | Issue                                                           | Severity | Fix                                                                                                                           |
|------|-----------------------------------------------------------------|----------|-------------------------------------------------------------------------------------------------------------------------------|
| 2A-1 | Password visibility state leaks between login/register modes    | Medium   | Reset `showPassword` and `showConfirmPassword` to `false` in `LaunchedEffect(isRegisterMode)`                                 |
| 2A-2 | No client-side password validation despite showing requirements | Medium   | Add `password.length >= 8 && password.any { it.isDigit() } && password.any { it.isLetter() }` to `canSubmit` in register mode |
| 2A-3 | Error message area reserves awkward 20dp min height when empty  | Medium   | Use `AnimatedVisibility` for error area                                                                                       |
| 2A-4 | Ghost "toggle mode" button lacks visible boundary               | Low      | Use `RwButtonVariant.Secondary` or add underline                                                                              |
| 2A-5 | Card height jumps abruptly when switching login/register        | Low      | Use `AnimatedVisibility` with vertical expand for confirm password section                                                    |
| 2A-6 | Password visibility toggle lacks tooltip                        | Low      | Wrap with `RwTooltip("Show password"/"Hide password")`                                                                        |
| 2A-7 | Unused `RwTooltip` import                                       | Low      | Remove unused import                                                                                                          |

### Stream 2B: ProjectEditor
> **Details:** [`issues/APP_ISSUES_PROJECT_EDITOR.md`](issues/APP_ISSUES_PROJECT_EDITOR.md)
**Files:** `DagEditorScreen.kt`, `DagCanvas.kt`, `DagEditorViewModel.kt`, `BlockPropertiesPanel.kt`, `TemplateAutocompleteField.kt`, `DagCanvasDrawing.kt`

| ID    | Issue                                                                                                | Severity | Fix                                                              |
|-------|------------------------------------------------------------------------------------------------------|----------|------------------------------------------------------------------|
| 2B-1  | **TemplateAutocompleteField uses raw OutlinedTextField**                                             | High     | Replace with `RwTextField` for visual consistency                |
| 2B-2  | **Sidebar toggle buttons only 24dp** (should be 44dp+)                                               | High     | Increase to at least 44dp with 24dp icons                        |
| 2B-3  | White text on light block colors fails WCAG contrast (GitHub Publication #34D399, Container #94A3B8) | Medium   | Use dark text on light block colors, or darken block fill colors |
| 2B-4  | "Fit" button resets to default instead of true zoom-to-fit                                           | Medium   | Compute bounds of all blocks and set zoom/pan to contain them    |
| 2B-5  | Blocks placed off-screen with no auto-pan                                                            | Medium   | Auto-pan/zoom to newly added block after placement               |
| 2B-6  | Zoom controls lack background — hard to read over grid                                               | Medium   | Add semi-transparent background card behind zoom controls        |
| 2B-7  | Canvas hint disappears when 2nd block added (should persist until first edge)                        | Medium   | Change condition to `graph.edges.isEmpty()`                      |
| 2B-8  | No visual feedback when block is added to canvas                                                     | Medium   | Flash/highlight or scroll-to-block animation                     |
| 2B-9  | Edge selection has no hover affordance                                                               | Medium   | Add visual highlight on hover proximity                          |
| 2B-10 | Right sidebar toggle button partially clipped when collapsed                                         | Medium   | Adjust positioning to prevent clipping                           |
| 2B-11 | Dirty indicator "*" uses error color (red) for normal state                                          | Low      | Use `onSurfaceVariant` or amber instead                          |
| 2B-12 | Properties panel empty state lacks visual weight                                                     | Low      | Add icon and stronger visual emphasis                            |

### Stream 2C: ProjectAutomation
> **Details:** [`issues/APP_ISSUES_PROJECT_AUTOMATION.md`](issues/APP_ISSUES_PROJECT_AUTOMATION.md)
**Files:** `ProjectAutomationScreen.kt`

| ID    | Issue                                                        | Severity | Fix                                                                     |
|-------|--------------------------------------------------------------|----------|-------------------------------------------------------------------------|
| 2C-1  | **Maven form Create button hidden below viewport**           | High     | Auto-scroll to bottom when form opens, or use `bringIntoViewRequester`  |
| 2C-2  | **Webhook secret field not visible in secret card**          | High     | Debug and fix: ensure secret value is populated in text field           |
| 2C-3  | Webhook URL not copyable                                     | Medium   | Add copy-to-clipboard icon button next to URL                           |
| 2C-4  | No max-width constraint on content area                      | Medium   | Add `widthIn(max = 720.dp).align(Alignment.TopCenter)`                  |
| 2C-5  | Cron expression uses body style instead of monospace         | Medium   | Change to `AppTypography.code`                                          |
| 2C-6  | Preset selector uses invisible click overlay — accessibility | Medium   | Add `Role.DropdownList` semantics                                       |
| 2C-7  | Inconsistent spacing between toggle and delete icon          | Medium   | Wrap in `Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm))` |
| 2C-8  | Empty state text lacks visual distinction (no icon)          | Low      | Add small illustrative icon to each section empty state                 |
| 2C-9  | Section spacing too tight (4dp)                              | Low      | Increase `Arrangement.spacedBy` to `Spacing.sm` (8dp)                   |
| 2C-10 | Webhook URL text truncation without ellipsis                 | Low      | Add `overflow = TextOverflow.Ellipsis`                                  |

### Stream 2D: ReleaseList
> **Details:** [`issues/APP_ISSUES_RELEASE_LIST.md`](issues/APP_ISSUES_RELEASE_LIST.md)
**Files:** `ReleaseListScreen.kt`, `ReleaseListViewModel.kt`

| ID   | Issue                                                           | Severity | Fix                                                                                             |
|------|-----------------------------------------------------------------|----------|-------------------------------------------------------------------------------------------------|
| 2D-1 | **Release items display raw UUID instead of project name**      | High     | Build `Map<ProjectId, String>` from `viewModel.projects` and resolve names in `ReleaseListItem` |
| 2D-2 | **Release title shows raw UUID fragment**                       | High     | Show sequential number, or project name + timestamp                                             |
| 2D-3 | Terminology mismatch: "Create Release" vs "Start release"       | Low      | Unify to "Start Release" across all strings                                                     |
| 2D-4 | Error state visually sparse (no icon)                           | Medium   | Add error icon, match empty state visual weight                                                 |
| 2D-5 | Start Release form missing placeholder text in project dropdown | Medium   | Add placeholder "Select a project"                                                              |
| 2D-6 | "Updated just now" ticker unique to this screen                 | Medium   | Either add to all list screens or remove from this one                                          |
| 2D-7 | Status filter vs project filter have inconsistent spacing       | Medium   | Standardize padding: both use `Spacing.sm` top padding                                          |
| 2D-8 | "Archived" status badge nearly invisible in dark mode           | Low      | Increase alpha or use different background approach                                             |
| 2D-9 | Release tooltip shows raw UUID                                  | Low      | Show "Release ID:" prefix with monospace or add copy affordance                                 |

### Stream 2E: ReleaseView
> **Details:** [`issues/APP_ISSUES_RELEASE_VIEW.md`](issues/APP_ISSUES_RELEASE_VIEW.md)
**Files:** `ReleaseDetailScreen.kt`, `ExecutionDagCanvas.kt`, `SubBuildsSection.kt`, `ErrorDetailSection.kt`, `ArtifactTreeView.kt`, `ReleaseDetailViewModel.kt`

| ID    | Issue                                                          | Severity | Fix                                                                    |
|-------|----------------------------------------------------------------|----------|------------------------------------------------------------------------|
| 2E-1  | TopAppBar title lacks release identification (no project name) | High     | Show project name + release identifier in title                        |
| 2E-2  | Status icons disappear at low zoom (<4f)                       | High     | Add fallback indicator (border color) when icons too small             |
| 2E-3  | Top bar actions change dramatically by status without anchor   | High     | Use consistent button positioning; always show same slots              |
| 2E-4  | No pan/zoom visible controls or fit-to-screen                  | Medium   | Add zoom controls overlay (zoom %, +/-, fit button) like ProjectEditor |
| 2E-5  | No release timeline/progress overview                          | Medium   | Add "X/Y blocks completed" progress bar in header                      |
| 2E-6  | Two "Stop" buttons with unclear scope distinction              | Medium   | Differentiate labels: "Stop Release" vs "Stop Block: {name}"           |
| 2E-7  | DAG blocks no hover cursor change                              | Medium   | Change cursor to pointer on block hover                                |
| 2E-8  | Loading state bare spinner with no context                     | Medium   | Add "Loading release..." text                                          |
| 2E-9  | Disconnected indicator text low contrast (error + caption)     | Medium   | Use body style with error color                                        |
| 2E-10 | Block detail panel max 350dp hard-coded                        | Medium   | Make responsive: use `weight` or `fraction` based                      |
| 2E-11 | Block detail panel full-width with no max-width                | Medium   | Add `widthIn(max = 800.dp)`                                            |
| 2E-12 | Block outputs no copy-to-clipboard                             | Low      | Add copy button per output entry                                       |
| 2E-13 | SubBuildsSection `clickable(indication = null)` — no ripple    | Medium   | Remove `indication = null` to show ripple                              |
| 2E-14 | SubBuild status icons use raw Unicode                          | Low      | Replace with Material icons                                            |
| 2E-15 | Outputs 32dp left indent (16+16 double padding)                | Medium   | Fix padding to align with rest of panel                                |

### Stream 2F: ConnectionList
> **Details:** [`issues/APP_ISSUES_CONNECTION_LIST.md`](issues/APP_ISSUES_CONNECTION_LIST.md)
**Files:** `ConnectionListScreen.kt`

| ID   | Issue                                                      | Severity | Fix                                                                               |
|------|------------------------------------------------------------|----------|-----------------------------------------------------------------------------------|
| 2F-1 | Connection type displayed 3 times redundantly              | Medium   | Remove subtitle type text; keep badge only; replace subtitle with useful metadata |
| 2F-2 | Missing metadata/timestamps in list items                  | Medium   | Add "owner/repo" for GitHub, "server URL" for TeamCity, or "Last updated"         |
| 2F-3 | No "Edit" button — clickable card not obvious              | Medium   | Add chevron (like ProjectList) or explicit "Edit" ghost button                    |
| 2F-4 | Delete button always visible in bright red — too prominent | Medium   | Move behind overflow menu, or use ghost variant until hover                       |
| 2F-5 | Webhook URL contrast marginal                              | Low      | Use code/monospace style for URL text                                             |

### Stream 2G: ConnectionForm
> **Details:** [`issues/APP_ISSUES_CONNECTION_FORM.md`](issues/APP_ISSUES_CONNECTION_FORM.md)
**Files:** `ConnectionFormScreen.kt`

| ID   | Issue                                                    | Severity | Fix                                                                                        |
|------|----------------------------------------------------------|----------|--------------------------------------------------------------------------------------------|
| 2G-1 | **No inline validation or field-level error indicators** | High     | Mark required fields with asterisk; add `isError` state on blur; show field-level messages |
| 2G-2 | No "Test Connection" button on form                      | Medium   | Add "Test Connection" button below config fields                                           |
| 2G-3 | No field descriptions/helper text                        | Medium   | Add supporting text to PAT, Owner, Repo, Server URL, Webhook URL fields                    |
| 2G-4 | Type selector accessibility — invisible click overlay    | Medium   | Add `Role.DropdownList` semantics                                                          |
| 2G-5 | Placeholder text duplicates label                        | Medium   | Use example values instead: "e.g., Production GitHub"                                      |
| 2G-6 | Section header lacks visual weight                       | Medium   | Use `AppTypography.heading` for section headers                                            |
| 2G-7 | Missing top padding between TopAppBar and form           | Medium   | Add `Spacing.xl` top padding                                                               |
| 2G-8 | Polling interval empty string defaults silently to 30    | Medium   | Show validation message when field is empty                                                |

### Stream 2H: TeamDetail
> **Details:** [`issues/APP_ISSUES_TEAM_DETAIL.md`](issues/APP_ISSUES_TEAM_DETAIL.md)
**Files:** `TeamDetailScreen.kt`, `TeamDetailViewModel.kt`

| ID    | Issue                                                         | Severity | Fix                                                                            |
|-------|---------------------------------------------------------------|----------|--------------------------------------------------------------------------------|
| 2H-1  | **Missing SnackbarHost** — only screen without one            | Critical | Add `SnackbarHost` to Scaffold                                                 |
| 2H-2  | **Error state replaces all loaded content**                   | High     | Use snackbar for action errors; reserve full-page error for load failures only |
| 2H-3  | **Description truncated to 2 lines on detail view**           | High     | Remove `maxLines` constraint or increase to 10+                                |
| 2H-4  | **No refresh button/indicator**                               | High     | Add `RefreshIconButton` + `LinearProgressIndicator`                            |
| 2H-5  | **Leave confirmation may appear off-screen**                  | High     | Place `RwInlineConfirmation` in fixed position above LazyColumn                |
| 2H-6  | Info card has no heading/anchor — team name only in TopAppBar | Medium   | Add team name as heading inside the card                                       |
| 2H-7  | No loading indicator during leave action                      | Medium   | Set `isLoading` during `leaveTeam` API call                                    |
| 2H-8  | No tooltips on toolbar buttons                                | Medium   | Wrap Back, Audit Log, Manage, Leave buttons with `RwTooltip`                   |
| 2H-9  | Collaborator badge low contrast                               | Medium   | Increase badge background alpha or use different color                         |
| 2H-10 | Warning icon semantically wrong for "leave"                   | Low      | Change to `Icons.AutoMirrored.Filled.ExitToApp`                                |
| 2H-11 | No empty state for members list                               | Medium   | Add "No members yet" message                                                   |
| 2H-12 | No bottom content padding on LazyColumn                       | Low      | Add `contentPadding = PaddingValues(bottom = Spacing.xl)`                      |
| 2H-13 | Warning icon missing contentDescription                       | Medium   | Add `contentDescription = "Warning"`                                           |

### Stream 2I: TeamList
> **Details:** [`issues/APP_ISSUES_TEAM_LIST.md`](issues/APP_ISSUES_TEAM_LIST.md)
**Files:** `TeamListScreen.kt`, `TeamListViewModel.kt`

| ID   | Issue                                             | Severity | Fix                                                                           |
|------|---------------------------------------------------|----------|-------------------------------------------------------------------------------|
| 2I-1 | Non-member cards look clickable but aren't        | Medium   | Add visual distinction (reduced opacity or lock icon) for non-clickable cards |
| 2I-2 | "Request to Join" lacks loading/disabled state    | Medium   | Track pending requests per team, disable button during API call               |
| 2I-3 | Create team navigates away immediately            | Medium   | Stay on list, show success snackbar with "Go to team" action                  |
| 2I-4 | "My Invites" ghost button lacks visual affordance | Low      | Add mail/envelope icon, or badge count                                        |

### Stream 2J: TeamManage
> **Details:** [`issues/APP_ISSUES_TEAM_MANAGE.md`](issues/APP_ISSUES_TEAM_MANAGE.md)
**Files:** `TeamManageScreen.kt`, `TeamManageViewModel.kt`

| ID    | Issue                                                     | Severity | Fix                                                                      |
|-------|-----------------------------------------------------------|----------|--------------------------------------------------------------------------|
| 2J-1  | No section dividers between Members/Invites/JoinRequests  | Medium   | Add `HorizontalDivider` between sections                                 |
| 2J-2  | "Promote"/"Demote" labels lack context                    | Medium   | Change to "Promote to Lead" / "Demote to Collaborator"                   |
| 2J-3  | No per-action loading states                              | Medium   | Disable buttons during API calls, show spinner                           |
| 2J-4  | No reject confirmation for join requests                  | Medium   | Add `RwInlineConfirmation` for reject action                             |
| 2J-5  | Invite form appears disconnected from trigger button      | Medium   | Move form to appear below "Invite User" button                           |
| 2J-6  | Save button scrolls out of view                           | Medium   | Show persistent "unsaved changes" banner when `hasEditChanges` is true   |
| 2J-7  | Pending Invites/Join Requests hidden when empty           | Low      | Always show section headers with count (even 0)                          |
| 2J-8  | Unicode checkmark in snackbar instead of localized string | Low      | Use `packStringResource(Res.string.teams_updated_success)`               |
| 2J-9  | Inconsistent section heading padding                      | Low      | Standardize to `padding(horizontal = Spacing.lg, vertical = Spacing.sm)` |
| 2J-10 | Hardcoded 80dp bottom spacer                              | Low      | Use `contentPadding` on LazyColumn instead                               |

### Stream 2K: MyInvites
> **Details:** [`issues/APP_ISSUES_MY_INVITES.md`](issues/APP_ISSUES_MY_INVITES.md)
**Files:** `MyInvitesScreen.kt`, `MyInvitesViewModel.kt`

| ID   | Issue                                                                | Severity | Fix                                                                   |
|------|----------------------------------------------------------------------|----------|-----------------------------------------------------------------------|
| 2K-1 | **Invite cards don't fill available width** — shrink-wrap to content | High     | Add `fillParentMaxWidth()` to LazyColumn items                        |
| 2K-2 | **Card layout (vertical Column) inconsistent with ListItemCard**     | High     | Refactor to use `ListItemCard` or replicate its horizontal Row layout |
| 2K-3 | No decline confirmation                                              | Medium   | Add `RwInlineConfirmation` for decline action                         |
| 2K-4 | No per-card loading state during accept/decline                      | Medium   | Track loading per invite, disable buttons during API call             |
| 2K-5 | No invite count badge on "My Invites" button                         | Medium   | Add `RwBadge` with pending invite count                               |
| 2K-6 | Decline button uses same blue as Accept                              | Low      | Use `onSurfaceVariant` for Decline button text                        |
| 2K-7 | No animation on card removal                                         | Low      | Add `animateItem()` to LazyColumn items                               |

### Stream 2L: AuditLog
> **Details:** [`issues/APP_ISSUES_AUDIT_LOG.md`](issues/APP_ISSUES_AUDIT_LOG.md)
**Files:** `AuditLogScreen.kt`, `AuditLogViewModel.kt`

| ID    | Issue                                                      | Severity    | Fix                                                                        |
|-------|------------------------------------------------------------|-------------|----------------------------------------------------------------------------|
| 2L-1  | **No filtering or search capability**                      | Medium-High | Add search field + target type filter chips                                |
| 2L-2  | Not using `ListItemCard` component                         | Medium      | Refactor `AuditEventItem` to use `ListItemCard` or add `verticalAlignment` |
| 2L-3  | No timestamp grouping or date separators                   | Medium      | Add date-based section headers (Today, Yesterday, etc.)                    |
| 2L-4  | Missing `contentPadding` on LazyColumn                     | Medium      | Add `contentPadding = PaddingValues(bottom = Spacing.lg)`                  |
| 2L-5  | Badge color doesn't vary by target type                    | Low-Medium  | Assign distinct colors per target type category                            |
| 2L-6  | Details text truncated to 2 lines with no expand           | Low-Medium  | Add click-to-expand or "show more"                                         |
| 2L-7  | Duplicated date formatting (not using `formatTimestamp()`) | Low         | Replace manual formatting with shared `formatTimestamp()` utility          |
| 2L-8  | Snackbar uses Dismiss instead of Retry                     | Low         | Change to Retry action, consistent with other screens                      |
| 2L-9  | TopAppBar title generic ("Audit Log")                      | Low         | Show "Audit Log — {Team Name}"                                             |
| 2L-10 | Empty state lacks explanatory text                         | Low         | Add "Audit events will appear here as team activity occurs"                |

### Stream 2M: ProjectList
> **Details:** [`issues/APP_ISSUES_PROJECT_LIST.md`](issues/APP_ISSUES_PROJECT_LIST.md)
**Files:** `ProjectListScreen.kt`, `ProjectListViewModel.kt`

| ID   | Issue                                                              | Severity | Fix                                                           |
|------|--------------------------------------------------------------------|----------|---------------------------------------------------------------|
| 2M-1 | DropdownMenu with single "Delete" item — unnecessary extra click   | Medium   | Show delete action directly in card row (like ConnectionList) |
| 2M-2 | No SnackbarHost — inconsistency with ConnectionListScreen          | Medium   | Add `SnackbarHost` to Scaffold                                |
| 2M-3 | Missing "Updated X ago" timestamp — inconsistency with ReleaseList | Medium   | Add timestamp or remove from ReleaseList for consistency      |
| 2M-4 | No description field in create project form                        | Medium   | Add description field to `CreateProjectInlineForm`            |
| 2M-5 | Three-dot menu lacks tooltip                                       | Low      | Add `RwTooltip("More options")`                               |
| 2M-6 | Error state has no dismiss mechanism                               | Low      | Add dismiss button or auto-clear on retry                     |

---

## Phase 3: Polish & Low-Priority Fixes

> **Details:** [`issues/APP_ISSUES_POLISH.md`](issues/APP_ISSUES_POLISH.md)

These can be done after Phase 1 and 2. Each is independent.

### Stream 3A: Animation & Transitions
| ID   | Issue                                               | Screen        | Fix                                     |
|------|-----------------------------------------------------|---------------|-----------------------------------------|
| 3A-1 | LoginScreen card height jumps on mode toggle        | LoginScreen   | AnimatedVisibility for confirm password |
| 3A-2 | MyInvites no animation on card removal              | MyInvites     | Add `animateItem()`                     |
| 3A-3 | Save button style abrupt transition (Ghost→Primary) | ProjectEditor | Use outlined style when clean           |

### Stream 3B: Tooltips & Accessibility Completions
| ID   | Issue                                          | Screen            | Fix                    |
|------|------------------------------------------------|-------------------|------------------------|
| 3B-1 | Password visibility toggle lacks tooltip       | LoginScreen       | Add RwTooltip          |
| 3B-2 | Toolbar buttons lack tooltips                  | TeamDetail        | Add RwTooltip to all   |
| 3B-3 | Section icons lack contentDescription          | ProjectAutomation | Add descriptions       |
| 3B-4 | Checkbox state not announced to screen readers | ProjectAutomation | Add state semantics    |
| 3B-5 | Warning icon missing contentDescription        | TeamDetail        | Add contentDescription |
| 3B-6 | Error banner dismiss lacks tooltip             | ConnectionForm    | Add RwTooltip          |

### Stream 3C: Minor Spacing & Padding
| ID   | Issue                                     | Screen               | Fix                        |
|------|-------------------------------------------|----------------------|----------------------------|
| 3C-1 | LazyColumn missing bottom contentPadding  | AuditLog, TeamDetail | Add contentPadding         |
| 3C-2 | Filter chip spacer too small (4dp)        | ConnectionList       | Increase to Spacing.sm     |
| 3C-3 | Outputs 32dp double-indent                | ReleaseView          | Fix padding nesting        |
| 3C-4 | Section heading padding inconsistency     | TeamManage           | Standardize padding        |
| 3C-5 | HorizontalDivider placement in Automation | ProjectAutomation    | Align with content padding |

### Stream 3D: Typography Refinements
| ID   | Issue                                                           | Screen      | Fix                                   |
|------|-----------------------------------------------------------------|-------------|---------------------------------------|
| 3D-1 | Login error text uses body (14sp) — too large for inline errors | LoginScreen | Use bodySmall/caption                 |
| 3D-2 | Login subtitle text borderline contrast                         | LoginScreen | Increase to ~#B0B8C4                  |
| 3D-3 | SubBuild status icons use Unicode                               | ReleaseView | Replace with Material icons           |
| 3D-4 | TopAppBar title missing maxLines/overflow                       | TeamList    | Add maxLines = 1, overflow = Ellipsis |

### Stream 3E: Information Density
| ID   | Issue                                          | Screen                      | Fix                            |
|------|------------------------------------------------|-----------------------------|--------------------------------|
| 3E-1 | Missing invite timestamp on invite cards       | MyInvites                   | Show "Invited X days ago"      |
| 3E-2 | No count indicator for filtered/total releases | ReleaseList                 | Add "Showing X of Y"           |
| 3E-3 | No sort controls                               | ProjectList, ConnectionList | Add sort dropdown (name, date) |
| 3E-4 | Missing metadata on project cards              | ProjectList                 | Add "Last edited X ago"        |

---

## Execution Strategy

```
Phase 1 (Foundation):  1A ──┐
                       1B ──┤
                       1C ──┤── all parallel, ~1 session
                       1D ──┤
                       1E ──┘

Phase 2 (Per-Screen):  2A (Login) ────────┐
                       2B (Editor) ───────┤
                       2C (Automation) ───┤
                       2D (ReleaseList) ──┤
                       2E (ReleaseView) ──┤
                       2F (ConnList) ─────┤── all 13 streams parallel
                       2G (ConnForm) ─────┤
                       2H (TeamDetail) ───┤
                       2I (TeamList) ─────┤
                       2J (TeamManage) ───┤
                       2K (MyInvites) ────┤
                       2L (AuditLog) ─────┤
                       2M (ProjectList) ──┘

Phase 3 (Polish):      3A (Animations) ──┐
                       3B (Tooltips) ────┤
                       3C (Spacing) ─────┤── all parallel, low priority
                       3D (Typography) ──┤
                       3E (Info Density) ─┘
```

### Priority Order Within Phase 2

If resources are limited, address screens in this order (by critical mass of high-severity issues):

1. **TeamDetail** (2H) — 1 critical + 4 high
2. **MyInvites** (2K) — 2 high
3. **ProjectEditor** (2B) — 2 high
4. **ProjectAutomation** (2C) — 2 high
5. **ReleaseList** (2D) — 2 high
6. **ReleaseView** (2E) — 3 high (complex screen)
7. **ConnectionForm** (2G) — 1 high
8. **AuditLog** (2L) — 1 medium-high
9. **TeamManage** (2J) — 6 medium
10. **ConnectionList** (2F) — 4 medium
11. **TeamList** (2I) — 4 medium
12. **ProjectList** (2M) — 4 medium
13. **LoginScreen** (2A) — 4 medium

### Verification

After each stream completes:
1. Run `./gradlew :composeApp:jvmTest` to verify no regressions
   1. Add tests if new behaviour is not covered by existing tests
2. Use `ui-verification` skill to visually verify the fixed screen
3. Run the 4 expert (UX, Design, Compose, QA) reviewers on changed files
4. Don't commit or switch worktree until said to do so
