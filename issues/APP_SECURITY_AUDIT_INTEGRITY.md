# APP_SECURITY_AUDIT_INTEGRITY

## Severity: High
## Category: Audit / Log Integrity
## Affected Screens: AuditLogScreen (data source)

### Description

Multiple audit integrity issues compound to undermine the reliability of the audit log:

**1. Silent write failures (High):** `AuditService.log()` fires audit writes asynchronously via `scope.launch` and catches all exceptions with only `logger.error()`. If the DB is down or the transaction fails, the audit event is permanently lost with no retry, no fallback queue, and no notification.

**2. Non-transactional writes (Medium):** All audit events are written *after* the business action completes via fire-and-forget coroutine. If the server crashes between the business transaction commit and the audit write, the event is permanently lost.

**3. No tamper protection (Medium):** The `audit_events` table has no write-once constraint, no cryptographic chaining, and no deletion protection. Anyone with DB access could modify or delete audit records.

**4. Events lost on team deletion (Medium):** `AuditEventTable.teamId` uses `ReferenceOption.SET_NULL` on delete. When a team is deleted, all associated audit events have their `teamId` set to NULL and become orphaned/unreachable.

### Impact

The audit log cannot be trusted as a complete or accurate record of actions. Server crashes, DB issues, or deliberate team deletion can create gaps or destroy evidence.

### Affected Locations

- `server/.../audit/AuditService.kt:23-39` — async fire-and-forget, catches all exceptions
- `server/.../persistence/AuditEventTable.kt:8` — `SET_NULL` on team delete
- `server/.../teams/TeamService.kt:86-93` — audit logged after action

### Recommendation

1. For critical security actions, write audit events within the same DB transaction as the business action (transactional outbox pattern).
2. Implement a fallback mechanism (file-based WAL or retry queue) for audit write failures.
3. Change `onDelete` for `teamId` to `NO_ACTION` or store team ID as a plain varchar.
4. Add DB-level triggers or policies to prevent UPDATE/DELETE on the audit table.
