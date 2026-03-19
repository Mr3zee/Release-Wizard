# Cross-Cutting Foundation Issues

These affect shared components or theme tokens used across multiple screens.

---

## 1A: Theme & Contrast Fixes

### 1A-1: Dark theme placeholder text fails WCAG AA
- **Severity:** Medium
- **File:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/theme/AppColors.kt`, line 240
- **Current:** `inputPlaceholder = Color(0xFF7B8494)` on `inputBg = Color(0xFF151820)` = ~3.7:1 contrast ratio
- **Required:** WCAG AA requires 4.5:1 for normal text (14sp)
- **Fix:** Change `inputPlaceholder` to `Color(0xFF9CA3AF)` or lighter (~5.4:1 ratio)
- **Screens affected:** Every screen with text fields (LoginScreen, ConnectionForm, ProjectList search, TeamList search, ReleaseList search, ProjectEditor properties, ProjectAutomation forms)

### 1A-2: Empty state icons too faint (0.5 alpha)
- **Severity:** Low
- **Files:**
  - `composeApp/src/commonMain/kotlin/com/github/mr3zee/projects/ProjectListScreen.kt`, line 211
  - `composeApp/src/commonMain/kotlin/com/github/mr3zee/connections/ConnectionListScreen.kt`, line 254
  - Similar pattern in TeamListScreen, ReleaseListScreen
- **Current:** `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)` — very faint in dark theme
- **Fix:** Increase alpha to `0.7f` across all empty state icons
- **Screens affected:** ProjectList, ConnectionList, TeamList, ReleaseList

### 1A-3: `onSurfaceVariant` overused for distinct content types
- **Severity:** Medium
- **Context:** Duration text, type labels, gate phase context, approval progress, stopped context, webhook placeholders, and general secondary text all use `onSurfaceVariant`. This makes it impossible to visually distinguish metadata categories.
- **Fix:** Introduce semantic color tokens (e.g., `chromeTextTimestamp`, `chromeTextMetadata`) or use the existing `chromeTextTertiary` for truly de-emphasized content while using `chromeTextSecondary` for important secondary info.
- **Screens affected:** ReleaseView (primary), AuditLog, ReleaseList

---

## 1B: RwButton Disabled State

### 1B-1: Disabled primary button not visually distinct
- **Severity:** Medium
- **File:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/components/RwButton.kt`, line 108
- **Current:** `Modifier.alpha(if (enabled) 1f else 0.6f)` — on dark backgrounds, 60% opacity blue still looks quite clickable
- **Fix:** For `Primary` variant disabled state, use a desaturated gray background (`chromeSurfaceSecondary`) with dim text (`chromeTextTertiary`) instead of a translucent version of the active color. For other variants, the alpha approach is acceptable.
- **Screens affected:** LoginScreen (Sign In/Create Account), ConnectionForm (Save), ProjectEditor (Save), all inline forms (Create buttons)

---

## 1C: RwInlineForm Enter-to-Submit

### 1C-1: No Enter key handler in inline forms
- **Severity:** Medium
- **File:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/components/RwInlineForm.kt`, lines 66-71
- **Current:** Only handles `Key.Escape` to dismiss. No `Key.Enter` handler.
- **Fix:** Add `Key.Enter` handling that triggers the form's submit action (passed as a new `onSubmit` parameter). Only trigger when the form is valid (delegate validation to caller).
- **Implementation:**
  ```kotlin
  // In RwInlineForm's onPreviewKeyEvent:
  Key.Enter -> {
      if (!event.isShiftPressed) { // Shift+Enter for newline in multiline fields
          onSubmit?.invoke()
          true
      } else false
  }
  ```
- **Screens affected:** TeamList (Create Team), ProjectList (Create Project), ReleaseList (Start Release)

---

## 1D: "No Search Results" Empty State Pattern

### 1D-1: Search-no-results states missing decorative icon
- **Severity:** Low
- **Context:** When a search query returns no results, all list screens show only text ("No results match your search.") and a "Clear search" button. The *primary* empty states (no data at all) include a decorative icon + text + CTA. This inconsistency makes the no-results state feel incomplete.
- **Files:**
  - `ProjectListScreen.kt`, lines 193-204
  - `ConnectionListScreen.kt`, lines 233-246
  - `TeamListScreen.kt` (similar pattern)
  - `ReleaseListScreen.kt`, lines 353-373
- **Fix:** Add a muted search icon (e.g., `Icons.Outlined.SearchOff` or `Icons.Outlined.Search` at 0.5 alpha) above the "No results" text in each list screen.

---

## 1E: ListItemCard Touch Targets

### 1E-1: Verify 44dp minimum touch targets for action buttons
- **Severity:** Low
- **File:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/components/ListItemCard.kt`
- **Context:** WCAG 2.5.8 requires 44x44dp minimum touch targets. `RwIconButton` should ensure this.
- **Fix:** Audit `RwIconButton` default size and add `Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)` if needed.
- **Screens affected:** All list screens with action buttons
