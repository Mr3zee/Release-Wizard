# Plugins, Persistence & Infrastructure Issues

16 findings: 2 Critical, 4 High, 8 Medium, 6 Low

## Critical

### ✅ INFRA-C1: `SecureRandom` instantiated per `encrypt()` call

**Files:** `EncryptionService.kt:21`

Entropy pool depletion under load. Thread-safe, should be singleton.

**Fix:** `private val secureRandom = SecureRandom()` as class-level field.

---

### INFRA-C2: `SchemaUtils.create` — no migration strategy; schema drift is silent

**Files:** `DatabaseFactory.kt:23-48`

`CREATE TABLE IF NOT EXISTS` silently succeeds when schema has diverged. Drift already occurred (commented-out orphaned table).

**Fix:** Adopt Flyway/Liquibase or use `SchemaUtils.createMissingTablesAndColumns`.

---

## High

### INFRA-H1: `RequestSizeLimit` does not terminate pipeline after 413

**Files:** `RequestSizeLimit.kt:19-34`

Route handler still executes after 413 response. Chunked bodies bypass entirely.

**Fix:** Call `finish()` after responding. Configure Netty `maxContentLength`.

---

### INFRA-H2: `CsrfProtection` — non-constant-time null token comparison

**Files:** `CsrfPlugin.kt:48-54`

`ByteArray(0)` vs 64-byte comparison returns immediately (timing side-channel).

**Fix:** Zeroed `ByteArray(session.csrfToken.length)`. Treat empty token as failure.

---

### INFRA-H3: DB lookup on every session refresh doubles read load

**Files:** `SessionTtlPlugin.kt:54-58`

**Fix:** Increase threshold or make lazy (only role-sensitive endpoints).

---

### INFRA-H4: Critical startup services swallow failures

**Files:** `Application.kt:286-323`

Server stays up in broken state if recovery or listener fails.

**Fix:** Treat as fatal or set health-check DOWN flag.

---

## Medium

### INFRA-M1: HikariCP missing keepaliveTime/idleTimeout
### INFRA-M2: CorrelationId ignores upstream header
### INFRA-M3: http:// CORS origins silently fail
### INFRA-M4: Health check hits DB every call, no rate limit
### INFRA-M5: `likeContains` relies on caller discipline
### INFRA-M6: Default postgres/postgres credentials in YAML
### INFRA-M7: `SECURE_COOKIE=false` without production guard
### INFRA-M8: Legacy session migration resets TTL

---

## Low

### INFRA-L1: Encryption key validation mismatch (chars vs bytes)
### INFRA-L2: `LockConflictException` missing correlationId
### INFRA-L3: `Cache-Control: no-store` conditional on session
### INFRA-L4: Root endpoint discloses version
### INFRA-L5: Exception messages echoed to clients
### INFRA-L6: `safeOffset` takes Int, truncates silently
