# APP_SECURITY_NO_ACTIVE_RELEASE_CHECK

## Severity: Medium
## Category: Business Logic / Data Integrity
## Affected Screens: ProjectListScreen

### Description

`deleteProject` does not check whether the project has active (RUNNING or PENDING) releases before deletion. The `ReleaseTable.projectTemplateId` has `onDelete = ReferenceOption.RESTRICT`, so the database rejects the delete if releases exist, but this produces an unhandled exception that surfaces as a generic 500 Internal Server Error.

Compare with `TeamRepository.deleteWithActiveReleaseCheck`, which explicitly checks before attempting deletion.

### Impact

A team lead attempting to delete a project with active releases receives an unhelpful "Internal server error" instead of an actionable message. If the DB constraint were ever relaxed, active releases would be orphaned.

### Affected Locations

- `server/.../projects/ProjectsService.kt:102-112` — no active release check
- `server/.../projects/ExposedProjectsRepository.kt:242-247` — relies on DB RESTRICT

### Recommendation

Add an explicit check for active releases (similar to team deletion) and return a 409 Conflict: "Cannot delete project with active releases."
