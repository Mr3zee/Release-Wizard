# APP_SECURITY_AUDIT_LOG_ACCESS

## Severity: Low
## Category: Authorization
## Affected Screens: AuditLogScreen

### Description

The audit log endpoint uses `checkMembership()` which allows any team member (including COLLABORATOR role) to view the full audit log. All other sensitive team operations require `checkTeamLead()`. The "Audit Log" button on TeamDetailScreen is visible to all members regardless of role.

Audit logs contain security-sensitive details: who invited whom, who was removed, role changes, and all administrative actions.

### Impact

Regular collaborators can monitor all administrative actions, which may be intentional for transparency but could enable social engineering or insider threat scenarios.

### Affected Locations

- `server/.../teams/TeamRoutes.kt:142` — uses `checkMembership` not `checkTeamLead`
- `composeApp/.../teams/TeamDetailScreen.kt:105-108` — button shown to all members

### Recommendation

Either restrict to TEAM_LEAD (change to `checkTeamLead`) and gate the UI button, or document this as an intentional transparency decision.
