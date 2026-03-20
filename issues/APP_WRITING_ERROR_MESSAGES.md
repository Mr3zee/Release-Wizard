# APP_WRITING_ERROR_MESSAGES — Terse and Non-Actionable Error Messages

Multiple error messages are too brief to help users understand what went wrong or how to
recover. Error messages should explain the situation and guide toward resolution.

## Issues

### GLOBAL_02 — "Invalid credentials" (HIGH)
**Current:** `"Invalid credentials"`
**Problem:** Does not guide the user toward a fix.
**Suggested:** `"Incorrect username or password. Please check your credentials and try again."`

### GLOBAL_03 — "Access denied" (HIGH)
**Current:** `"Access denied"`
**Problem:** Two words with no context about what was denied or what to do.
**Suggested:** `"You don't have permission to perform this action. Contact your team lead if you need access."`

### GLOBAL_04 — "Not found" (HIGH)
**Current:** `"Not found"`
**Problem:** Does not say what was not found.
**Suggested:** `"The requested item could not be found. It may have been deleted or moved."`

### GLOBAL_05 — "Invalid request" (MEDIUM)
**Current:** `"Invalid request"`
**Problem:** Developer-speak (HTTP 400). Users don't know what "request" means here.
**Suggested:** `"Something went wrong with your input. Please review your changes and try again."`

### GLOBAL_06 — "Server error. Please try again." (MEDIUM)
**Current:** `"Server error. Please try again."`
**Problem:** Retrying a 500-class error is often futile. No fallback guidance.
**Suggested:** `"Something went wrong on the server. Please try again, and if the problem persists, contact your administrator."`

### GLOBAL_15 — "Registration failed. Username may already be taken." (MEDIUM)
**Current:** `"Registration failed. Username may already be taken."`
**Problem:** "may" introduces doubt. Should be definitive if the server can detect duplicates.
**Suggested:** Split into: `"That username is already taken."` (duplicate) and
`"Registration failed. Please try again."` (other).

### LOGIN_02 — "Invalid credentials" on login (MEDIUM)
Same as GLOBAL_02. The string `error_invalid_credentials` is used across the app.

### LOGIN_03 — Registration failed path (MEDIUM)
The localized `UiMessage.RegistrationFailed` is never constructed by `toUiMessage()` — the
raw server string is shown instead. Dead code path means localized message is never used.

### EDITOR_07 — "Could not acquire editing lock" (MEDIUM)
**Current:** `"Could not acquire editing lock"`
**Suggested:** `"Could not acquire editing lock. Check your connection and try again."`

### AUTOMATION_11 — "Invalid cron syntax" (HIGH)
**Current:** `"Invalid cron syntax"`
**Problem:** Not actionable — does not explain valid format.
**Suggested:** `"Invalid format — expected 5 fields (e.g., 0 9 * * 1-5)."`

### CONNFORM_12 — Polling interval silent coercion (LOW)
Entering out-of-range values (e.g., `1` or `999`) shows no error; value is silently coerced
at save time. Add `isError` state with `"Must be between 5 and 300"`.

### GLOBAL_07 — Inconsistent punctuation in errors (LOW)
Some error messages end with periods, others don't. Adopt a consistent convention.

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| GLOBAL_02/LOGIN_02 | Fixed | "Incorrect username or password. Please check your credentials and try again." |
| GLOBAL_03 | Fixed | "You don't have permission to perform this action. Contact your team lead if you need access." |
| GLOBAL_04 | Fixed | "The requested item could not be found. It may have been deleted or moved." |
| GLOBAL_05 | Fixed | "Something went wrong with your input. Please review your changes and try again." |
| GLOBAL_06 | Fixed | "Something went wrong on the server. Please try again, and if the problem persists, contact your administrator." |
| GLOBAL_15 | Fixed | Split into `error_registration_failed` + `error_username_taken` |
| LOGIN_03 | Fixed | Added INVALID_CREDENTIALS and REGISTRATION_FAILED code-based mapping in `toUiMessage()` |
| EDITOR_07 | Fixed | "Could not acquire the editing lock. Someone else may be editing, or there may be a connection issue." |
| AUTOMATION_11 | Fixed | "Invalid format — expected 5 fields (e.g., 0 9 * * 1-5)" |
| CONNFORM_12 | Fixed | Added `connections_polling_out_of_range` = "Must be between 5 and 300 seconds" |
| GLOBAL_07 | Fixed | Standardized punctuation across error messages |
