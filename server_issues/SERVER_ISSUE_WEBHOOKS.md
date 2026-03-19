# Webhooks Issues

11 findings: 3 High, 7 Medium, 1 Low

## High

### HOOK-H1: Token not invalidated after first successful use — replay within 24h TTL

**Files:** `StatusWebhookService.kt`, `WebhookRoutes.kt:63-66`

**Fix:** Deactivate token on first successful `Accepted` response.

---

### HOOK-H2: Block not RUNNING returns same 404 as bad token

**Files:** `StatusWebhookService.kt:68`

**Fix:** Return 410 Gone or 409 Conflict for non-RUNNING blocks.

---

### HOOK-H3: `create` does not deactivate existing active token

**Files:** `ExposedStatusWebhookTokenRepository.create:38-49`

On block retry, both old and new tokens are active simultaneously.

**Fix:** Call `deactivate(releaseId, blockId)` inside `create` in the same transaction.

---

## Medium

### HOOK-M1: No rate limiting on public webhook endpoint
### HOOK-M2: Raw bearer token written to INFO log
### HOOK-M3: Payload size check bypassable via chunked transfer
### HOOK-M4: `(release_id, block_id)` index non-unique
### HOOK-M5: Token active-check and write not atomic
### HOOK-M6: `baseUrl` concatenation has no trailing-slash normalization
### HOOK-M7: `cleanupExpiredTokens` called only at startup

---

## Low

### HOOK-L1: `deactivateToken` silently succeeds on no-op
