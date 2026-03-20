# Application Security Plan

Security audit of the Release Wizard Compose Desktop/Web application.
Conducted by running a dedicated security expert per screen (13 screens), then consolidating and deduplicating findings into unique issues.

## Summary

| Severity | Count |
|----------|-------|
| High     | 3     |
| Medium   | 16    |
| Low      | 6     |
| **Total**| **25**|

**Screens audited:** LoginScreen, ProjectListScreen, ReleaseListScreen, ReleaseDetailScreen, ConnectionListScreen, ConnectionFormScreen, TeamListScreen, TeamDetailScreen, TeamManageScreen, AuditLogScreen, MyInvitesScreen, ProjectAutomationScreen, DagEditorScreen

**Positive findings:** Server-side authorization is consistently enforced via `TeamAccessService`. CSRF protection is properly implemented. SQL injection is prevented via Exposed parameterized queries. XSS is not a concern in Compose UI. UUIDs are used for all resource IDs. Session authentication covers all authenticated routes.

---

## Priority 1 — High Severity

| # | Issue | File |
|---|-------|------|
| 1 | Masked secrets overwrite real secrets on connection update | [APP_SECURITY_MASKED_SECRET_OVERWRITE](issues/APP_SECURITY_MASKED_SECRET_OVERWRITE.md) |
| 2 | Missing audit logging for critical actions (15+ actions defined but never logged) | [APP_SECURITY_MISSING_AUDIT_CRITICAL_ACTIONS](issues/APP_SECURITY_MISSING_AUDIT_CRITICAL_ACTIONS.md) |
| 3 | Audit log integrity failures (silent writes, non-transactional, no tamper protection, lost on delete) | [APP_SECURITY_AUDIT_INTEGRITY](issues/APP_SECURITY_AUDIT_INTEGRITY.md) |

## Priority 2 — Medium Severity (Security Controls)

| # | Issue | File |
|---|-------|------|
| 4 | Session cookie signed but not encrypted — payload readable | [APP_SECURITY_SESSION_COOKIE_NOT_ENCRYPTED](issues/APP_SECURITY_SESSION_COOKIE_NOT_ENCRYPTED.md) |
| 5 | Open registration with no verification or admin approval | [APP_SECURITY_OPEN_REGISTRATION](issues/APP_SECURITY_OPEN_REGISTRATION.md) |
| 6 | Default HTTP transport — no HTTPS enforcement for Desktop client | [APP_SECURITY_DEFAULT_HTTP_TRANSPORT](issues/APP_SECURITY_DEFAULT_HTTP_TRANSPORT.md) |
| 7 | Account lockout state in-memory only — lost on restart | [APP_SECURITY_ACCOUNT_LOCKOUT_IN_MEMORY](issues/APP_SECURITY_ACCOUNT_LOCKOUT_IN_MEMORY.md) |

## Priority 3 — Medium Severity (Input Validation & Injection)

| # | Issue | File |
|---|-------|------|
| 8 | URL validation missing — accepts arbitrary schemes and non-HTTPS | [APP_SECURITY_URL_VALIDATION_MISSING](issues/APP_SECURITY_URL_VALIDATION_MISSING.md) |
| 9 | DAG graph unbounded — no limits on blocks, edges, nesting, name/param lengths | [APP_SECURITY_DAG_GRAPH_UNBOUNDED](issues/APP_SECURITY_DAG_GRAPH_UNBOUNDED.md) |
| 10 | Template expression injection in block parameters | [APP_SECURITY_TEMPLATE_EXPRESSION_INJECTION](issues/APP_SECURITY_TEMPLATE_EXPRESSION_INJECTION.md) |
| 11 | External config/path injection — missing URL encoding in external API calls | [APP_SECURITY_EXTERNAL_CONFIG_INJECTION](issues/APP_SECURITY_EXTERNAL_CONFIG_INJECTION.md) |

## Priority 4 — Medium Severity (Data Exposure & Abuse)

| # | Issue | File |
|---|-------|------|
| 12 | Insufficient rate limiting — only login endpoint is rate-limited | [APP_SECURITY_INSUFFICIENT_RATE_LIMITING](issues/APP_SECURITY_INSUFFICIENT_RATE_LIMITING.md) |
| 13 | SSRF via DNS rebinding — TOCTOU gap in URL validation | [APP_SECURITY_SSRF_DNS_REBINDING](issues/APP_SECURITY_SSRF_DNS_REBINDING.md) |
| 14 | Sensitive data exposed in API responses (DAG snapshots, parameters, block outputs) | [APP_SECURITY_DATA_EXPOSURE_IN_RESPONSES](issues/APP_SECURITY_DATA_EXPOSURE_IN_RESPONSES.md) |
| 15 | Secret suffix leak — masked values reveal last 4 characters | [APP_SECURITY_SECRET_SUFFIX_LEAK](issues/APP_SECURITY_SECRET_SUFFIX_LEAK.md) |
| 16 | Webhook endpoint abuse — no rate limiting, no per-project trigger limits | [APP_SECURITY_WEBHOOK_ENDPOINT_ABUSE](issues/APP_SECURITY_WEBHOOK_ENDPOINT_ABUSE.md) |

## Priority 5 — Medium Severity (Authorization)

| # | Issue | File |
|---|-------|------|
| 18 | Client-side auth guards bypassable — manage screen, delete/archive buttons | [APP_SECURITY_CLIENT_SIDE_AUTH_GUARDS](issues/APP_SECURITY_CLIENT_SIDE_AUTH_GUARDS.md) |
| 19 | Self role modification via API — leads can demote/remove themselves | [APP_SECURITY_SELF_ROLE_MODIFICATION](issues/APP_SECURITY_SELF_ROLE_MODIFICATION.md) |

## Priority 6 — Medium Severity (Business Logic)

| # | Issue | File |
|---|-------|------|
| 20 | No active release check on project delete — unhelpful 500 error | [APP_SECURITY_NO_ACTIVE_RELEASE_CHECK](issues/APP_SECURITY_NO_ACTIVE_RELEASE_CHECK.md) |

## Priority 7 — Low Severity

| # | Issue | File |
|---|-------|------|
| 21 | Error messages disclose internal details (hostnames, IPs, stack traces) | [APP_SECURITY_ERROR_MESSAGE_DISCLOSURE](issues/APP_SECURITY_ERROR_MESSAGE_DISCLOSURE.md) |
| 22 | Input length limits missing across all text fields | [APP_SECURITY_INPUT_LENGTH_LIMITS](issues/APP_SECURITY_INPUT_LENGTH_LIMITS.md) |
| 23 | Secrets persist in client memory and clipboard | [APP_SECURITY_SECRETS_IN_CLIENT_MEMORY](issues/APP_SECURITY_SECRETS_IN_CLIENT_MEMORY.md) |
| 24 | Name sanitization missing — no trim, no character filtering | [APP_SECURITY_NAME_SANITIZATION](issues/APP_SECURITY_NAME_SANITIZATION.md) |
| 25 | Expired invites shown to users and team leads | [APP_SECURITY_EXPIRED_INVITES_SHOWN](issues/APP_SECURITY_EXPIRED_INVITES_SHOWN.md) |

## Informational (Not Filed as Issues)

These were identified but not filed as issues — they are design decisions or accepted trade-offs:

| Topic | Notes |
|-------|-------|
| No team scoping in API list calls | Client doesn't pass `activeTeamId`; server correctly authorizes. See [APP_SECURITY_NO_TEAM_SCOPING](issues/APP_SECURITY_NO_TEAM_SCOPING.md) |
| WebSocket security edge cases | Origin check fail-open, auth retry, sequence gaps. See [APP_SECURITY_WEBSOCKET_SECURITY](issues/APP_SECURITY_WEBSOCKET_SECURITY.md) |
| Audit log access open to all members | May be intentional transparency. See [APP_SECURITY_AUDIT_LOG_ACCESS](issues/APP_SECURITY_AUDIT_LOG_ACCESS.md) |

---

## Cross-Cutting Observations

### What's done well
- **Server-side authorization** is consistently enforced via `TeamAccessService` (checkMembership, checkTeamLead)
- **CSRF protection** with constant-time comparison on all mutating HTTP methods
- **SQL injection prevention** via Exposed ORM parameterized queries with proper LIKE escaping
- **Atomic DB operations** with `SELECT ... FOR UPDATE` for race-condition-prone flows (invites, locks, approvals)
- **UUID v4 resource IDs** prevent enumeration
- **SSRF dual-check** (validateUrlNotPrivate + SsrfProtection plugin) for connection testing
- **Argon2 password hashing** with timing normalization to prevent user enumeration on login

### Systemic gaps
1. **Rate limiting** only covers login — all other endpoints are unprotected
2. **Input length validation** is inconsistent — some fields validated, most are not
3. **Audit logging** has major gaps — many defined actions are never logged
4. **URL/scheme validation** is incomplete — HTTP accepted where HTTPS should be required
5. **Client-side guards** don't match server-side authorization — showing actions users can't perform
