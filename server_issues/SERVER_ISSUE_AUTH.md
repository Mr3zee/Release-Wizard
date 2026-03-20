# Auth Issues

17 findings: 6 High, 7 Medium, 5 Low

## High

### ✅ AUTH-H1: `/me` outside `authenticate` block — fragile TTL interaction

**Files:** `AuthRoutes.kt:107-119`

**Fix:** Move `/me` inside `authenticate("session-auth")` and remove the manual null-check.

---

### ✅ AUTH-H2: `requireAdminSession` dead null-check inside authenticated routes

**Files:** `AuthRoutes.kt:170-181`

**Fix:** Replace with `call.userSession()` and perform only the role check.

---

### ✅ AUTH-H3: Argon2 instance thread-safety unverified

**Files:** `AuthService.kt:37, 41, 59, 64, 81, 90`

**Fix:** Verify thread-safety, synchronize with a `Mutex`, or create a new instance per call.

---

### ✅ AUTH-H4: No per-username account lockout

**Files:** `AuthRoutes.kt`, `Application.kt:146`

IP rate limit only. No defense against distributed credential stuffing.

**Fix:** Add per-username counter with exponential back-off (5 failures -> 15 min lockout).

---

### ✅ AUTH-H5: Argon2 parallelism p=1 (under-configured vs OWASP)

**Files:** `AuthService.kt:41, 81, 90`

OWASP recommends p=4 minimum.

**Fix:** Increase parallelism to at least 4. Update `DUMMY_HASH`.

---

### ✅ AUTH-H6: CSRF plugin fails open when `csrfToken` is empty string

**Files:** `CsrfPlugin.kt:45`, `UserSession.kt:11`

Empty token permanently bypasses CSRF enforcement.

**Fix:** Reject requests with empty token. Remove default `csrfToken = ""`.

---

## Medium

### AUTH-M1: Dummy hash inside SERIALIZABLE transaction holds lock ~400ms
### AUTH-M2: Unsafe `updateUserRole` is public
### AUTH-M3: `TeamRepository` injected per-request inside handler lambda
### AUTH-M4: Session role cached — 60s demotion lag window
### ✅ AUTH-M5: No absolute session lifetime
### AUTH-M6: IP rate limiting may collapse behind reverse proxy
### AUTH-M7: `corsConfig()` silently accepts empty origins list

---

## Low

### AUTH-L1: Empty CSRF token bypass — migration path never closes
### AUTH-L2: Log messages differentiate "not found" vs "wrong password"
### AUTH-L3: `UserTable.username` varchar(255) vs route limit of 64
### AUTH-L4: Test password violates production policy
### AUTH-L5: Whitespace satisfies `requireSpecial` check
