# APP_SECURITY_MISSING_AUDIT_CRITICAL_ACTIONS

## Severity: High
## Category: Audit / Log Completeness
## Affected Screens: AuditLogScreen (missing data)

### Description

Several security-critical actions defined in the `AuditAction` enum are never actually logged by any service:

- **RELEASE_RERUN** — `ReleasesService.rerunRelease()` creates a new release without audit logging
- **RELEASE_ARCHIVED** — `ReleasesService.archiveRelease()` archives without logging
- **RELEASE_DELETED** — `ReleasesService.deleteRelease()` deletes without logging
- **BLOCK_RESTARTED** — `ReleasesService.restartBlock()` restarts without logging
- **BLOCK_APPROVED** — no usage anywhere
- **PROJECT_UPDATED** — `ProjectsService.updateProject()` updates without logging
- **USER_LOGIN / USER_REGISTER** — defined but never written; only go to `logger.info()`
- **SCHEDULE_CREATED/UPDATED/DELETED** — `ScheduleService` never uses `AuditService`
- **TRIGGER_CREATED/UPDATED/DELETED/FIRED** — `TriggerService` never uses `AuditService`

### Impact

The audit log has significant blind spots. Destructive actions (release deletion, project modification) and authentication events (login, failed login, registration) are not recorded. An attacker could delete releases, modify projects, create malicious triggers/schedules, or perform account enumeration, all without any entry in the audit log.

### Affected Locations

- `server/.../releases/ReleasesService.kt` — rerun (~188), archive (~235), delete (~244), restartBlock (~304)
- `server/.../projects/ProjectsService.kt:74` — updateProject
- `server/.../auth/AuthRoutes.kt` — login/register
- `server/.../schedules/ScheduleService.kt` — entire file, no audit calls
- `server/.../triggers/TriggerService.kt` — entire file, no audit calls
- `server/.../mavenpublication/MavenTriggerService.kt` — entire file, no audit calls
- `shared/.../model/AuditEvent.kt` — enum defines unused actions

### Recommendation

Add `auditService.log()` calls for every action that has a corresponding `AuditAction` enum value, especially destructive operations and authentication events. Remove any enum values that are intentionally not logged, or document why they are deferred.
