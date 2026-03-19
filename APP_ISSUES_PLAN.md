# APP_ISSUES_PLAN.md — Comprehensive UX/Design Fix Plan

Full audit of all 13 screens produced **224 issues** (15 critical/high, 76 medium, 133 low).
This plan groups issues into **parallel work streams** by affected area. Within each phase, all streams can execute concurrently.

## Detailed Issue Files

Each group has a dedicated file with full descriptions, file locations, and fix instructions:

| File | Scope |
|------|-------|
| [`issues/APP_ISSUES_FOUNDATION.md`](issues/APP_ISSUES_FOUNDATION.md) | Cross-cutting: theme, contrast, shared components |
| [`issues/APP_ISSUES_LOGIN.md`](issues/APP_ISSUES_LOGIN.md) | LoginScreen (7 issues) |
| [`issues/APP_ISSUES_PROJECT_EDITOR.md`](issues/APP_ISSUES_PROJECT_EDITOR.md) | ProjectEditor (12 issues) |
| [`issues/APP_ISSUES_PROJECT_AUTOMATION.md`](issues/APP_ISSUES_PROJECT_AUTOMATION.md) | ProjectAutomation (10 issues) |
| [`issues/APP_ISSUES_RELEASE_LIST.md`](issues/APP_ISSUES_RELEASE_LIST.md) | ReleaseList (9 issues) |
| [`issues/APP_ISSUES_RELEASE_VIEW.md`](issues/APP_ISSUES_RELEASE_VIEW.md) | ReleaseView (25 issues) |
| [`issues/APP_ISSUES_CONNECTION_LIST.md`](issues/APP_ISSUES_CONNECTION_LIST.md) | ConnectionList (5 issues) |
| [`issues/APP_ISSUES_CONNECTION_FORM.md`](issues/APP_ISSUES_CONNECTION_FORM.md) | ConnectionForm (8 issues) |
| [`issues/APP_ISSUES_TEAM_DETAIL.md`](issues/APP_ISSUES_TEAM_DETAIL.md) | TeamDetail (13 issues) |
| [`issues/APP_ISSUES_TEAM_LIST.md`](issues/APP_ISSUES_TEAM_LIST.md) | TeamList (4 issues) |
| [`issues/APP_ISSUES_TEAM_MANAGE.md`](issues/APP_ISSUES_TEAM_MANAGE.md) | TeamManage (10 issues) |
| [`issues/APP_ISSUES_MY_INVITES.md`](issues/APP_ISSUES_MY_INVITES.md) | MyInvites (7 issues) |
| [`issues/APP_ISSUES_AUDIT_LOG.md`](issues/APP_ISSUES_AUDIT_LOG.md) | AuditLog (10 issues) |
| [`issues/APP_ISSUES_PROJECT_LIST.md`](issues/APP_ISSUES_PROJECT_LIST.md) | ProjectList (6 issues) |
| [`issues/APP_ISSUES_POLISH.md`](issues/APP_ISSUES_POLISH.md) | Low-priority polish (animations, tooltips, spacing, typography, info density) |

---

## Phase 1: Cross-Cutting Foundation (all screens benefit) — ✅ COMPLETE

> **Details:** [`issues/APP_ISSUES_FOUNDATION.md`](issues/APP_ISSUES_FOUNDATION.md)

| ID | Issue | Status |
|----|-------|--------|
| 1A-1 | Dark theme placeholder WCAG AA contrast | ✅ Fixed — `#9CA3AF` (~6.5:1) |
| 1A-2 | Empty state icons too faint (0.5f alpha) | ✅ Fixed — 0.7f across 6 screens |
| 1A-3 | `onSurfaceVariant` overused — add semantic tokens | ✅ Fixed — `chromeTextTimestamp` + `chromeTextMetadata` tokens added |
| 1B-1 | Disabled primary button insufficient distinction | ✅ Fixed — desaturated bg + dim text |
| 1C-1 | No Enter-to-submit in inline forms | ✅ Fixed — `onSubmit` param + KeyDown handler |
| 1D-1 | No search results missing decorative icon | ✅ Fixed — search icon at 0.5f in 4 screens |
| 1E-1 | RwIconButton 44dp touch targets | ✅ Fixed — 40dp → 44dp |

---

## Phase 2: Per-Screen Fixes — High/Critical Priority (parallel streams)

### Stream 2A: LoginScreen — ✅ DONE
| ID | Issue | Status |
|----|-------|--------|
| 2A-1 | Password visibility state leaks between modes | ✅ Reset visibility on mode toggle |
| 2A-2 | No client-side password validation | ⏭️ Skipped — server controls requirements, client validation would mismatch |
| 2A-3 | Error area reserves 20dp when empty | ✅ AnimatedVisibility for error |
| 2A-4 | Toggle mode button lacks boundary | ⏭️ Deferred to Phase 3 |
| 2A-5 | Card height jumps on login/register toggle | ✅ AnimatedVisibility with expandVertically |
| 2A-6 | Password visibility toggle lacks tooltip | ✅ RwTooltip added to both toggles |
| 2A-7 | Unused RwTooltip import | ✅ Now used by 2A-6 |

### Stream 2B: ProjectEditor — ✅ DONE (targeted fixes)
| ID | Issue | Status |
|----|-------|--------|
| 2B-1 | TemplateAutocompleteField uses raw OutlinedTextField | ⏭️ Complex refactor, deferred |
| 2B-2 | Sidebar toggle buttons only 24dp | ✅ Increased to 44dp |
| 2B-3–2B-6 | Canvas contrast, zoom-to-fit, auto-pan, zoom bg | ⏭️ Complex canvas work, deferred |
| 2B-7 | Canvas hint disappears too soon | ✅ Condition changed to `graph.edges.isEmpty()` |
| 2B-8–2B-10 | Block feedback, edge hover, sidebar clipping | ⏭️ Complex canvas work, deferred |
| 2B-11 | Dirty indicator uses error color | ✅ Changed to `onSurfaceVariant` |
| 2B-12 | Properties panel empty state | ⏭️ Deferred to Phase 3 |

### Stream 2C: ProjectAutomation — ✅ DONE (targeted fixes)
| ID | Issue | Status |
|----|-------|--------|
| 2C-1 | Maven form button hidden below viewport | ⏭️ Complex scroll management, deferred |
| 2C-2 | Webhook secret field not visible | ⏭️ Requires runtime debugging |
| 2C-3 | Webhook URL not copyable | ⏭️ Deferred |
| 2C-4 | No max-width on content area | ✅ Added `widthIn(max = 720.dp)` |
| 2C-5 | Cron expression uses body instead of monospace | ✅ Changed to `AppTypography.code` |
| 2C-6–2C-9 | Accessibility, spacing, empty states | ⏭️ Deferred |
| 2C-10 | Webhook URL truncation without ellipsis | ✅ Added `TextOverflow.Ellipsis` + monospace |

### Stream 2D: ReleaseList — ✅ DONE
| ID | Issue | Status |
|----|-------|--------|
| 2D-1 | Release items display raw UUID | ✅ Project name resolved via `projectNameMap` |
| 2D-2 | Release title shows raw UUID fragment | ✅ Title shows "ProjectName — timestamp" |
| 2D-3 | Terminology mismatch | ⏭️ String change deferred |
| 2D-4 | Error state visually sparse | ✅ Added Warning icon |
| 2D-5 | Dropdown missing placeholder | ✅ Added "Select a project" placeholder |
| 2D-6 | Updated ticker unique to this screen | ⏭️ Design decision deferred |
| 2D-7 | Filter spacing inconsistent | ✅ Standardized to `Spacing.sm` |
| 2D-8–2D-9 | Archived badge, tooltip | ⏭️ Deferred to Phase 3 |

### Stream 2E: ReleaseView — ✅ DONE (targeted fix)
| ID | Issue | Status |
|----|-------|--------|
| 2E-1–2E-7 | Canvas/panel complex issues | ⏭️ Complex canvas work, deferred |
| 2E-8 | Loading state bare spinner | ✅ Added "Loading release…" text |
| 2E-9–2E-15 | Contrast, panels, padding | ⏭️ Deferred |

### Stream 2F: ConnectionList — ✅ DONE
| ID | Issue | Status |
|----|-------|--------|
| 2F-1 | Type displayed 3 times redundantly | ✅ Removed subtitle text, keep badge only |
| 2F-2 | Missing metadata in list items | ⏭️ Requires model enrichment |
| 2F-3 | No edit affordance | ✅ Added chevron arrow |
| 2F-4 | Delete button too prominent | ✅ Changed to ghost + error color |
| 2F-5 | Webhook URL contrast | ✅ Changed to monospace `AppTypography.code` |

### Stream 2G: ConnectionForm — ✅ DONE (targeted fixes)
| ID | Issue | Status |
|----|-------|--------|
| 2G-1 | No inline validation | ⏭️ Complex validation logic, deferred |
| 2G-2 | No "Test Connection" button | ⏭️ Requires API integration |
| 2G-3 | No field descriptions | ⏭️ Deferred |
| 2G-4 | Type selector accessibility | ⏭️ Deferred |
| 2G-5 | Placeholder text duplicates label | ✅ Example values for all fields |
| 2G-6 | Section header lacks visual weight | ✅ Changed to `AppTypography.heading` |
| 2G-7 | Missing top padding | ✅ Added spacing above first field |
| 2G-8 | Polling interval defaults silently | ⏭️ Deferred |

### Stream 2H: TeamDetail — ✅ DONE (all 13 issues)
| ID | Issue | Status |
|----|-------|--------|
| 2H-1 | Missing SnackbarHost | ✅ Added to Scaffold |
| 2H-2 | Error replaces content | ✅ Snackbar for action errors, full-page for load only |
| 2H-3 | Description truncated | ✅ maxLines removed |
| 2H-4 | No refresh | ✅ RefreshIconButton + LinearProgressIndicator |
| 2H-5 | Leave confirmation off-screen | ✅ Fixed position above LazyColumn |
| 2H-6 | No heading in info card | ✅ Team name heading added |
| 2H-7 | No leave loading state | ✅ `isLeaving` state + disabled button |
| 2H-8 | No toolbar tooltips | ✅ RwTooltip on all buttons |
| 2H-9 | Badge low contrast | ✅ Using `chromeTextMetadata` token |
| 2H-10 | Wrong leave icon | ✅ Changed to ExitToApp |
| 2H-11 | No empty members state | ✅ "No members yet" with icon |
| 2H-12 | No bottom padding | ✅ contentPadding added |
| 2H-13 | Missing contentDescription | ✅ Added to leave icon |

### Stream 2I: TeamList — ✅ DONE (targeted fix)
| ID | Issue | Status |
|----|-------|--------|
| 2I-1 | Non-member cards look clickable | ✅ Reduced opacity (0.7f) |
| 2I-2 | Join request lacks loading state | ⏭️ Requires ViewModel changes |
| 2I-3 | Create team navigates immediately | ⏭️ Behavior change deferred |
| 2I-4 | My Invites button lacks affordance | ⏭️ Deferred to Phase 3 |

### Stream 2J: TeamManage — ✅ DONE
| ID | Issue | Status |
|----|-------|--------|
| 2J-1 | No section dividers | ✅ HorizontalDivider between all sections |
| 2J-2 | Promote/Demote labels lack context | ✅ "Promote to Lead" / "Demote to Collaborator" |
| 2J-3 | No per-action loading states | ⏭️ Requires ViewModel changes |
| 2J-4 | No reject confirmation | ⏭️ Deferred |
| 2J-5 | Invite form disconnected | ✅ Moved below "Invite User" button |
| 2J-6 | Save button scrolls out of view | ⏭️ Requires sticky header or banner |
| 2J-7 | Sections hidden when empty | ✅ Headers always visible with count |
| 2J-8 | Unicode checkmark in snackbar | ✅ Localized string |
| 2J-9 | Inconsistent heading padding | ⏭️ Minor, deferred |
| 2J-10 | Hardcoded 80dp bottom spacer | ✅ contentPadding on LazyColumn |

### Stream 2K: MyInvites — ✅ DONE
| ID | Issue | Status |
|----|-------|--------|
| 2K-1 | Cards don't fill width | ✅ ListItemCard layout (full-width) |
| 2K-2 | Vertical layout inconsistent | ✅ Refactored to horizontal ListItemCard |
| 2K-3 | No decline confirmation | ✅ RwInlineConfirmation added |
| 2K-4 | No per-card loading state | ✅ `loadingInviteIds` tracking + spinner |
| 2K-5 | No invite count badge | ⏭️ Cross-screen state, deferred |
| 2K-6 | Decline button same color | ✅ `onSurfaceVariant` color |
| 2K-7 | No card removal animation | ✅ `animateItem()` added |

### Stream 2L: AuditLog — ✅ DONE (targeted fixes)
| ID | Issue | Status |
|----|-------|--------|
| 2L-1 | No filtering/search | ⏭️ Complex feature, deferred |
| 2L-2–2L-3 | ListItemCard, date grouping | ⏭️ Structural refactor, deferred |
| 2L-4 | Missing contentPadding | ✅ Added `PaddingValues(bottom = Spacing.lg)` |
| 2L-5–2L-7 | Badge colors, expand, formatting | ⏭️ Deferred |
| 2L-8 | Snackbar uses Dismiss | ✅ Changed to Retry with refresh action |
| 2L-9 | Generic title | ⏭️ Deferred |
| 2L-10 | Empty state lacks hint | ✅ Added explanatory text |

### Stream 2M: ProjectList — ✅ DONE (targeted fix)
| ID | Issue | Status |
|----|-------|--------|
| 2M-1 | Single-item DropdownMenu | ⏭️ Deferred |
| 2M-2 | No SnackbarHost | ✅ Added hoisted SnackbarHostState |
| 2M-3–2M-4 | Timestamps, description field | ⏭️ Deferred |
| 2M-5 | Menu lacks tooltip | ⏭️ Conflicts with DropdownMenu popup |
| 2M-6 | Error no dismiss | ⏭️ Deferred |

---

## Phase 3: Polish & Low-Priority Fixes

> **Details:** [`issues/APP_ISSUES_POLISH.md`](issues/APP_ISSUES_POLISH.md)

These can be done after Phase 1 and 2. Each is independent.

### Stream 3A: Animation & Transitions
| ID | Issue | Screen | Fix |
|----|-------|--------|-----|
| 3A-1 | LoginScreen card height jumps on mode toggle | LoginScreen | AnimatedVisibility for confirm password |
| 3A-2 | MyInvites no animation on card removal | MyInvites | Add `animateItem()` |
| 3A-3 | Save button style abrupt transition (Ghost→Primary) | ProjectEditor | Use outlined style when clean |

### Stream 3B: Tooltips & Accessibility Completions
| ID | Issue | Screen | Fix |
|----|-------|--------|-----|
| 3B-1 | Password visibility toggle lacks tooltip | LoginScreen | Add RwTooltip |
| 3B-2 | Toolbar buttons lack tooltips | TeamDetail | Add RwTooltip to all |
| 3B-3 | Section icons lack contentDescription | ProjectAutomation | Add descriptions |
| 3B-4 | Checkbox state not announced to screen readers | ProjectAutomation | Add state semantics |
| 3B-5 | Warning icon missing contentDescription | TeamDetail | Add contentDescription |
| 3B-6 | Error banner dismiss lacks tooltip | ConnectionForm | Add RwTooltip |

### Stream 3C: Minor Spacing & Padding
| ID | Issue | Screen | Fix |
|----|-------|--------|-----|
| 3C-1 | LazyColumn missing bottom contentPadding | AuditLog, TeamDetail | Add contentPadding |
| 3C-2 | Filter chip spacer too small (4dp) | ConnectionList | Increase to Spacing.sm |
| 3C-3 | Outputs 32dp double-indent | ReleaseView | Fix padding nesting |
| 3C-4 | Section heading padding inconsistency | TeamManage | Standardize padding |
| 3C-5 | HorizontalDivider placement in Automation | ProjectAutomation | Align with content padding |

### Stream 3D: Typography Refinements
| ID | Issue | Screen | Fix |
|----|-------|--------|-----|
| 3D-1 | Login error text uses body (14sp) — too large for inline errors | LoginScreen | Use bodySmall/caption |
| 3D-2 | Login subtitle text borderline contrast | LoginScreen | Increase to ~#B0B8C4 |
| 3D-3 | SubBuild status icons use Unicode | ReleaseView | Replace with Material icons |
| 3D-4 | TopAppBar title missing maxLines/overflow | TeamList | Add maxLines = 1, overflow = Ellipsis |

### Stream 3E: Information Density
| ID | Issue | Screen | Fix |
|----|-------|--------|-----|
| 3E-1 | Missing invite timestamp on invite cards | MyInvites | Show "Invited X days ago" |
| 3E-2 | No count indicator for filtered/total releases | ReleaseList | Add "Showing X of Y" |
| 3E-3 | No sort controls | ProjectList, ConnectionList | Add sort dropdown (name, date) |
| 3E-4 | Missing metadata on project cards | ProjectList | Add "Last edited X ago" |

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
2. Use `ui-verification` skill to visually verify the fixed screen
3. Run the 7 expert reviewers on changed files
