# APP_WRITING_PLAN — UX Copy & Tech Writing Audit

Tech Writer expert review of all 14 screens in the Compose app. Each screen was reviewed
independently for clarity, consistency, completeness, accessibility, grammar, tone, action
labels, error messages, placeholder text, and jargon.

**Total issues found: 181 across 14 screens**

## Summary by Severity

| Severity | Count | Description |
|----------|-------|-------------|
| HIGH     | 19    | Broken UX, misleading info, runtime risks |
| MEDIUM   | 61    | Confusing copy, accessibility gaps, inconsistencies |
| LOW      | 101   | Polish, minor tone/style, nice-to-haves |

## Issue Files by Theme

### Critical (fix first)

| # | Issue File | Issues | Key Problems |
|---|-----------|--------|--------------|
| 1 | [APP_WRITING_PASSWORD_POLICY](issues/APP_WRITING_PASSWORD_POLICY.md) | 3 | Client says "8 chars" but server requires 12 — guaranteed registration failures |
| 2 | [APP_WRITING_MISSING_AUDIT_ACTION](issues/APP_WRITING_MISSING_AUDIT_ACTION.md) | 1 | `ADMIN_ACCESS` has no display name — potential runtime crash |
| 3 | [APP_WRITING_HARDCODED_STRINGS](issues/APP_WRITING_HARDCODED_STRINGS.md) | 3 | 17+ strings bypass localization/language-pack system |
| 4 | [APP_WRITING_RETRY_MISMATCH](issues/APP_WRITING_RETRY_MISMATCH.md) | 4 | "Retry" buttons perform wrong action (reload vs retry) |
| 5 | [APP_WRITING_ERROR_MESSAGES](issues/APP_WRITING_ERROR_MESSAGES.md) | 12 | "Access denied", "Not found", "Invalid credentials" — terse, non-actionable |

### High Priority

| # | Issue File | Issues | Key Problems |
|---|-----------|--------|--------------|
| 6 | [APP_WRITING_ORPHAN_STRINGS](issues/APP_WRITING_ORPHAN_STRINGS.md) | 15 | 25+ strings defined but never used — dead translation burden |
| 7 | [APP_WRITING_CONFIRMATIONS](issues/APP_WRITING_CONFIRMATIONS.md) | 10 | Destructive confirmations don't explain consequences |
| 8 | [APP_WRITING_ACTION_LABELS](issues/APP_WRITING_ACTION_LABELS.md) | 10 | "Leave", "Fit", "Test" — vague or misleading buttons |
| 9 | [APP_WRITING_PLACEHOLDERS](issues/APP_WRITING_PLACEHOLDERS.md) | 7 | No required-field indicators; label=placeholder redundancy |
| 10 | [APP_WRITING_EMPTY_STATES](issues/APP_WRITING_EMPTY_STATES.md) | 9 | Empty states that don't guide users on what to do next |

### Accessibility

| # | Issue File | Issues | Key Problems |
|---|-----------|--------|--------------|
| 11 | [APP_WRITING_CANVAS_ACCESSIBILITY](issues/APP_WRITING_CANVAS_ACCESSIBILITY.md) | 3 | DAG canvas invisible to screen readers |
| 12 | [APP_WRITING_ACCESSIBILITY_ICONS](issues/APP_WRITING_ACCESSIBILITY_ICONS.md) | 15 | Meaningful icons with null contentDescription |
| 13 | [APP_WRITING_ACCESSIBILITY_LOADING](issues/APP_WRITING_ACCESSIBILITY_LOADING.md) | 9 | Loading spinners with no accessible labels |

### Consistency & Polish

| # | Issue File | Issues | Key Problems |
|---|-----------|--------|--------------|
| 14 | [APP_WRITING_TERMINOLOGY](issues/APP_WRITING_TERMINOLOGY.md) | 13 | "step" vs "block", "Sign in" vs "Register" vs "Create Account" |
| 15 | [APP_WRITING_CAPITALIZATION](issues/APP_WRITING_CAPITALIZATION.md) | 8 | Mixed Title Case / sentence case across buttons and labels |
| 16 | [APP_WRITING_SORT_LABELS](issues/APP_WRITING_SORT_LABELS.md) | 5 | "Newest first" sorts by updatedAt, not createdAt |
| 17 | [APP_WRITING_JARGON](issues/APP_WRITING_JARGON.md) | 9 | "POST", "Cron Expression", "${" syntax in user-facing hints |
| 18 | [APP_WRITING_PUNCTUATION](issues/APP_WRITING_PUNCTUATION.md) | 6 | ASCII `...` vs Unicode `…`, trailing periods, embedded symbols |
| 19 | [APP_WRITING_THEME_AND_SIDEBAR](issues/APP_WRITING_THEME_AND_SIDEBAR.md) | 15 | "Theme: Auto" ambiguity, panel tooltips, role action wording |

## Screens Reviewed

| Screen | HIGH | MED | LOW | Total |
|--------|------|-----|-----|-------|
| LoginScreen | 1 | 4 | 6 | 11 |
| ProjectListScreen | 0 | 2 | 8 | 10 |
| DagEditorScreen | 2 | 7 | 9 | 18 |
| ProjectAutomationScreen | 2 | 5 | 10 | 17 |
| ReleaseListScreen | 1 | 4 | 7 | 12 |
| ReleaseDetailScreen | 2 | 6 | 8 | 16 |
| ConnectionListScreen | 2 | 5 | 6 | 13 |
| ConnectionFormScreen | 2 | 5 | 6 | 13 |
| TeamListScreen | 1 | 3 | 6 | 10 |
| TeamDetailScreen | 1 | 3 | 5 | 9 |
| TeamManageScreen | 2 | 5 | 6 | 13 |
| MyInvitesScreen | 1 | 4 | 4 | 9 |
| AuditLogScreen | 1 | 3 | 6 | 10 |
| Sidebar/Navigation (Global) | 3 | 6 | 11 | 20 |
| **Total** | **21** | **62** | **98** | **181** |

## Cross-Cutting Themes

1. **Localization discipline** — Hardcoded strings and orphan resources undermine the
   language-pack system. Fix hardcoded strings first, then audit orphans.

2. **Error message quality** — Global error messages are the #1 user frustration point.
   Every error should answer: what happened, why, and what to do next.

3. **Accessibility gaps** — Canvas-based UIs, loading states, and icon descriptions
   represent the largest accessibility debt. Canvas semantics is the hardest fix.

4. **Confirmation UX** — Destructive actions should explain consequences, not ask
   "are you sure?" Unused title strings should be wired to inline confirmations.

5. **Naming consistency** — Pick one casing convention (sentence case recommended),
   one verb per action ("Start" not "Create" for releases), and use it everywhere.

6. **Retry integrity** — Every "Retry" action must actually retry what failed, not
   reload the page. This is partly a code fix, partly a copy fix.
