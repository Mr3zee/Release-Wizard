# Cross-Cutting Foundation Issues

These affect shared components or theme tokens used across multiple screens.

---

## 1A: Theme & Contrast Fixes

### 1A-1: Dark theme placeholder text fails WCAG AA — ✅ DONE
- **Severity:** Medium
- **Status:** Fixed — `inputPlaceholder` changed from `#7B8494` to `#9CA3AF` (~6.5:1 contrast ratio, well above WCAG AA 4.5:1)

### 1A-2: Empty state icons too faint (0.5 alpha) — ✅ DONE
- **Severity:** Low
- **Status:** Fixed — alpha increased from `0.5f` to `0.7f` across 6 screens (ProjectList, ConnectionList, TeamList, ReleaseList, AuditLog, MyInvites)

### 1A-3: `onSurfaceVariant` overused for distinct content types — ✅ DONE
- **Severity:** Medium
- **Status:** Fixed — added `chromeTextTimestamp` and `chromeTextMetadata` semantic tokens to AppColors. Phase 2 screens will consume these tokens to replace `onSurfaceVariant` usages.
  - `chromeTextTimestamp`: light `#7C8598` (4.7:1), dark `#8A94A6` (5.3:1) — for durations, dates, temporal info
  - `chromeTextMetadata`: light `#566275` (6.2:1), dark `#B0B8C4` (8.5:1) — for type badges, counts, structural labels

---

## 1B: RwButton Disabled State

### 1B-1: Disabled primary button not visually distinct — ✅ DONE
- **Severity:** Medium
- **Status:** Fixed — disabled Primary buttons use `chromeSurfaceSecondary` background + `chromeTextTertiary` text. Alpha 0.6f skipped for Primary (explicit disabled colors are sufficient). `LocalContentColor` correctly propagated via `CompositionLocalProvider`. Other variants retain alpha-only approach.

---

## 1C: RwInlineForm Enter-to-Submit

### 1C-1: No Enter key handler in inline forms — ✅ DONE
- **Severity:** Medium
- **Status:** Fixed — added `onSubmit: (() -> Unit)? = null` parameter. Enter (KeyDown only, not Shift+Enter) triggers submission. Escape also restricted to KeyDown only (fixed double-fire bug found in review). Wired up in ProjectList, TeamList, ReleaseList with validation guards.

---

## 1D: "No Search Results" Empty State Pattern

### 1D-1: Search-no-results states missing decorative icon — ✅ DONE
- **Severity:** Low
- **Status:** Fixed — added `Icons.Default.Search` at 0.5f alpha above "No results" text in all 4 list screens (ProjectList, ConnectionList, TeamList, ReleaseList). Uses standard Search icon since `SearchOff` requires material-icons-extended dependency.

---

## 1E: ListItemCard Touch Targets

### 1E-1: Verify 44dp minimum touch targets for action buttons — ✅ DONE
- **Severity:** Low
- **Status:** Fixed — `RwIconButton` default size increased from 40dp to 44dp (WCAG 2.5.8 compliant).
