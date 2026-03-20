# APP_SECURITY_EXPIRED_INVITES_SHOWN

## Severity: Low
## Category: Data Integrity
## Affected Screens: MyInvitesScreen, TeamManageScreen

### Description

Both `findPendingInvitesByUser` and `findPendingInvitesByTeam` filter only by `status = PENDING` but do NOT filter out expired invites (where `expiresAt < now`). Compare with `findExistingPendingInvite` which correctly excludes expired rows.

Users see expired invites they cannot act on. Clicking "Accept" results in "Invite has expired" error.

### Impact

Misleading UX — users/team leads see stale expired invites. Not exploitable since expiry is enforced at write-time.

### Affected Locations

- `server/.../teams/TeamRepository.kt:578-583` — `findPendingInvitesByUser` missing expiry filter
- `server/.../teams/TeamRepository.kt:571-576` — `findPendingInvitesByTeam` missing expiry filter

### Recommendation

Add expiry filter to both queries: `and ((TeamInviteTable.expiresAt.isNull()) or (TeamInviteTable.expiresAt greater now))`.
