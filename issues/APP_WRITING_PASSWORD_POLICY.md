# APP_WRITING_PASSWORD_POLICY — Password Requirements Mismatch

## Issue

### LOGIN_01 — Client hint contradicts server policy (HIGH)

**Current client text:** `"At least 8 characters with a number and letter"`

**Actual server policy** (from `PasswordPolicyConfig`): minimum **12** characters, requires
an **uppercase** letter and a **digit**, optionally requires a special character.

The client-side hint understates the minimum length by 4 characters and omits the
uppercase requirement. Users following this guidance will have their registration
rejected by the server.

### LOGIN_11 — No username format guidance (LOW)
The server enforces max 64 characters for usernames but the client provides no hint
about allowed characters or length limits. Validation errors arrive as raw server strings.

### LOGIN_09 — Account lockout message is hardcoded on server (LOW)
Server returns `"Account temporarily locked. Try again later."` as a raw English string.
Bypasses localization. Also omits lockout duration even though it's available.

## Fix

- Update `auth_password_requirements` to: `"At least 12 characters, including an uppercase letter and a number"`
- Or better: fetch the policy from the server so the hint stays in sync automatically.
- Add a `supportingText` hint for the username field in register mode: `"Up to 64 characters."`
- Create a typed `UiMessage.AccountLocked(duration)` so the lockout message can be localized.

## Resolution

**Status:** RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| LOGIN_01 | Fixed | Updated `auth_password_requirements` to "At least 12 characters, including an uppercase letter and a number" |
| LOGIN_11 | Fixed | Added `auth_username_hint` ("Up to 64 characters") shown in register mode |
| LOGIN_09 | Minor | Server-side lockout message not addressed (server code change out of scope) |
