# Notifications Issues

12 findings: 2 Critical, 4 High, 4 Medium, 2 Low

## Critical

### NOTIF-C1: Fire-and-forget delivery — no retry, no persistence, no dead-letter

**Files:** `NotificationListener.kt`

SharedFlow with `replay=0` and `DROP_OLDEST`. No retry on Slack failures. Effectively less-than-once delivery.

**Fix:** Persist to DB queue. Process with retry logic and dead-letter handling.

---

### ✅ NOTIF-H1: SSRF via unvalidated webhook URL

**Files:** `NotificationListener.kt:95`, `NotificationConfig.kt:11`

Any authenticated team member can register a URL targeting internal services.

**Fix:** Validate URL: require `https://`, reject private IPs, allowlist `hooks.slack.com`.

---

## High

### NOTIF-H2: Ownership bypass when `userId` is empty
### NOTIF-H3: `update()` implemented but unguarded — orphaned attack surface
### ✅ NOTIF-H4: Webhook URL (secret credential) returned in API responses
### NOTIF-H5: No HTTP response status validation in `sendSlackNotification`
### NOTIF-H6: Listener has no stop/cancel mechanism

---

## Medium

### NOTIF-M1: `CancellationException` swallowed in event collection
### NOTIF-M2: No per-project notification config limit
### NOTIF-M3: Cross-team leakage — all members see all configs
### NOTIF-M4: `type: String` redundant and can diverge from config discriminator

---

## Low

### NOTIF-L1: `webhookUrl` not validated at creation time
### NOTIF-L2: `update()` dead API surface
