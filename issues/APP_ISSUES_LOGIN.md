# LoginScreen Issues

**Screen:** LoginScreen (pre-auth, login + register flows)
**File:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/auth/LoginScreen.kt`
**ViewModel:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/auth/AuthViewModel.kt`

---

## 2A-1: Password visibility state leaks between modes
- **Severity:** Medium
- **Location:** Lines 50-51 (state), line 57 (`LaunchedEffect(isRegisterMode)`)
- **Problem:** When user toggles password visibility in Register mode (`showPassword = true`), then switches back to Login mode, the password field still shows plaintext. The `LaunchedEffect(isRegisterMode)` only resets `confirmPassword`, not visibility states.
- **Fix:** Add `showPassword = false` and `showConfirmPassword = false` inside the `LaunchedEffect(isRegisterMode)` block:
  ```kotlin
  LaunchedEffect(isRegisterMode) {
      confirmPassword = ""
      showPassword = false
      showConfirmPassword = false
  }
  ```

## 2A-2: No client-side password validation despite showing requirements
- **Severity:** Medium
- **Location:** Lines 61-62 (`canSubmit` check)
- **Problem:** UI displays "At least 8 characters with a number and letter" in register mode, but `canSubmit` only checks `isNotBlank()` + passwords match. User can submit a 1-char password.
- **Fix:** Add validation to `canSubmit` in register mode:
  ```kotlin
  val canSubmit = username.isNotBlank() && password.isNotBlank() && (
      !isRegisterMode || (
          password == confirmPassword &&
          password.length >= 8 &&
          password.any { it.isDigit() } &&
          password.any { it.isLetter() }
      )
  )
  ```

## 2A-3: Error message area reserves awkward 20dp min height when empty
- **Severity:** Medium
- **Location:** Line 199 — `Box(modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp))`
- **Problem:** Always reserves 20dp between last text field and button, visible as dead space when no error.
- **Fix:** Replace with `AnimatedVisibility(visible = error != null)` wrapping the error Text. This eliminates the reserved space and smoothly expands when an error appears.

## 2A-4: Ghost "toggle mode" button lacks visible boundary
- **Severity:** Low
- **Location:** Line 230 — `RwButtonVariant.Ghost`
- **Problem:** "Don't have an account? Register" appears as plain blue text with no button affordance (no border, no underline). Users may not recognize it as interactive.
- **Fix:** Change to `RwButtonVariant.Secondary` (has visible border) or add underline text decoration.

## 2A-5: Card height jumps on login/register toggle
- **Severity:** Low
- **Location:** Lines 159-195 (confirm password section)
- **Problem:** Adding/removing the Confirm Password field causes ~120dp vertical jump because the card is centered.
- **Fix:** Wrap the confirm password section in `AnimatedVisibility(visible = isRegisterMode, enter = expandVertically(), exit = shrinkVertically())`.

## 2A-6: Password visibility toggle lacks tooltip
- **Severity:** Low
- **Location:** Lines 132-141
- **Problem:** `RwIconButton` without `RwTooltip` wrapper. `RwTooltip` is imported but not used.
- **Fix:** Wrap with `RwTooltip(tooltip = if (showPassword) "Hide password" else "Show password")`.

## 2A-7: Unused `RwTooltip` import
- **Severity:** Low
- **Location:** Line 32
- **Fix:** Remove unused import (or use it per 2A-6).
