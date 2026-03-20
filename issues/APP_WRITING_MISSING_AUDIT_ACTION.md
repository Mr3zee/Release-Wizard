# APP_WRITING_MISSING_AUDIT_ACTION — Missing Display Name for ADMIN_ACCESS

## Issue

### AUDIT_01 — ADMIN_ACCESS enum has no displayName mapping (HIGH)

`ADMIN_ACCESS` exists in the `AuditAction` enum and is actively logged by the server
(`TeamService.kt`), but has no branch in `AuditAction.displayName()` (in
`StringResources.kt`) and no string resource in `strings.xml`.

**Impact:** If an `ADMIN_ACCESS` event reaches the client, the `when` expression will
either fail to compile (exhaustive check) or crash at runtime. Server admins viewing
team details will never see a readable label for this event.

## Fix

1. Add string resource: `audit_action_admin_access` = `"Admin viewed team"`
   (or `"Admin access"`)
2. Add branch in `AuditAction.displayName()`:
   `AuditAction.ADMIN_ACCESS -> Res.string.audit_action_admin_access`
3. Add themed overrides in all 6 language packs.

## Resolution

**Status:** ALREADY RESOLVED

| Issue | Status | Notes |
|-------|--------|-------|
| AUDIT_01 | Already fixed | Was already fixed before this audit — `ADMIN_ACCESS` has both a string resource and a `displayName()` mapping |
