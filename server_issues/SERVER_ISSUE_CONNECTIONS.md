# Connections Issues

17 findings: 2 Critical, 6 High, 5 Medium, 6 Low

## Critical

### ✅ CONN-C1: SSRF via DNS rebinding — TOCTOU between validation and HTTP request

**Files:** `ConnectionTester.kt:validateUrlNotPrivate`

DNS resolved at check time, HTTP client resolves again. Only called for TeamCity, not GitHub or Slack.

**Fix:** Configure custom DNS resolver that re-applies private-IP check on every resolution.

---

### ✅ CONN-C2: No size cap on fetched GitHub YAML before Base64 decode + SnakeYAML parse

**Files:** `ConnectionTester.kt:fetchGitHubWorkflowInputs`

**Fix:** Check `json["size"]` before decoding. Reject above 512 KB.

---

## High

### ✅ CONN-H1: Encryption key validation mismatch (config: chars >= 32, service: bytes == 32)
### ✅ CONN-H2: SSRF protection missing for Slack — only prefix check
### ✅ CONN-H4: No audit log for `updateConnection`
### CONN-H5: Two DB round-trips per privileged request
### ✅ CONN-H6: Delete TOCTOU — three non-transactional DB ops

---

## Medium

### ✅ CONN-M1: Raw exception messages forwarded to API responses
### ✅ CONN-M2: No-op PUT bumps `updatedAt` and returns 200
### ✅ CONN-M3: Blank-name validation missing from update path
### CONN-M4: GitHub workflow list truncated at 100
### CONN-M5: TeamCity build-type list hardcoded at 5000

---

## Low

### CONN-L1: Masking shows last-4 (industry practice is first-4)
### CONN-L2: No key versioning
### ✅ CONN-L3: `workflowFile` not validated against filename format
### CONN-L4: Slack test never contacts Slack
### ✅ CONN-L5: `SecureRandom` per encrypt call
### ✅ CONN-L6: `webhookUrl()` dead code
