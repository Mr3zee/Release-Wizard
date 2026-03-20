# APP_QA_LOGIN — LoginScreen Test Coverage Gaps

## Screen: LoginScreen (`auth/LoginScreen.kt`)
**Existing tests:** 12 tests in `LoginScreenTest.kt`
**Behaviors identified:** 38 | **Covered:** 14 | **Gaps:** 16

---

## HIGH Priority

### QA-LOGIN-1: Register button disabled when confirmPassword is empty
In register mode with valid username+password, the button must still be disabled until confirmPassword is filled. Core form validation for the registration path.

### QA-LOGIN-2: Register button disabled when passwords do not match
`canSubmit` requires `password == confirmPassword`; this gate is never exercised. A regression would allow registration with mismatched passwords.

### QA-LOGIN-3: Register button enabled only when all three fields match
The positive case for register-mode `canSubmit` is not tested (existing register test only checks server error, not button enabled state).

### QA-LOGIN-4: Confirm password mismatch inline error
The field turns red and shows "Passwords do not match" when the user has typed a non-empty value that doesn't match. No test asserts this text or the `isError` state.

### QA-LOGIN-5: Loading state disables submit button and shows spinner
`isLoading == true` must disable the button and replace its text with a `CircularProgressIndicator`. No test injects a slow/in-flight response. A regression could allow double-submission.

---

## MEDIUM Priority

### QA-LOGIN-6: Mode switch resets confirmPassword and show-password flags
Switching from register back to login (or login→register) empties `confirmPassword` and resets both show-password toggles to false.

### QA-LOGIN-7: Confirm-password visibility toggle works independently
`login_confirm_password_toggle_visibility` flips `showConfirmPassword` without affecting `showPassword`. Not tested at all.

### QA-LOGIN-8: Password requirements hint visible in register mode only
The `supportingText` lambda is conditionally attached. No test checks for this text.

### QA-LOGIN-9: Error clears when typing in confirm-password field
`onValueChange` calls `viewModel.dismissError()` on the confirm-password field. Only username and password variants are tested.

### QA-LOGIN-10: Enter key in password field (login mode) submits the form
Keyboard navigation for form submission is fully untested.

### QA-LOGIN-11: Enter key in password field (register mode) advances focus to confirm-password
The branching keyboard handler is untested. Incorrect behavior would silently submit with an empty confirmPassword.

### QA-LOGIN-12: Enter key in confirm-password field submits the form
None of the Enter-key flows are tested.

### QA-LOGIN-13: Toggle button text changes correctly
"Already have an account? Sign in" / "Need an account? Register" label flip is not verified.

---

## LOW Priority

### QA-LOGIN-14: Confirm password mismatch does NOT trigger while empty
The early-exit guard `confirmPassword.isNotEmpty()` means an empty field should never show as an error.

### QA-LOGIN-15: Confirm-password field does not exist in login mode
The `AnimatedVisibility` wrapping it is controlled by `isRegisterMode`; no test asserts the field is absent before switching modes.

### QA-LOGIN-16: Successful login navigates (user state set in ViewModel)
No test verifies that after a successful mock login response, `viewModel.user` becomes non-null.
