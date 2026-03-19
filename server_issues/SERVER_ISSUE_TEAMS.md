# Teams Issues

16 findings: 2 Critical, 5 High, 7 Medium, 3 Low

## Critical

### ✅ TEAM-C1: TOCTOU race on last-owner enforcement

**Files:** `TeamService.kt:108-113, 122-127, 136-140`

Count and mutate in separate transactions. Two demotions can leave zero leads.

**Fix:** Single `suspendTransaction` with `SELECT FOR UPDATE`.

---

### ✅ TEAM-C2: TOCTOU race on invite accept / join request approve — duplicate memberships

**Files:** `TeamService.kt:238-242, 214-217`

Three separate transactions. Concurrent accepts create duplicate membership rows (500).

**Fix:** Single transaction. Catch duplicate key as 409.

---

## High

### TEAM-H1: Admin bypass ignores last-lead mutation guard
### TEAM-H2: Invite uniqueness constraint logically broken (includes status)
### TEAM-H3: Join request uniqueness has same flaw
### TEAM-H4: `approveJoinRequest` missing membership check
### TEAM-H5: No invite expiry — stale PENDING invites valid indefinitely

---

## Medium

### TEAM-M1: Route-level DI anti-pattern for /audit and /tags
### TEAM-M2: `listTeams` leaks all team names
### TEAM-M3: Both-null update is silent no-op with spurious audit
### TEAM-M4: `findMembership` fetches username on every access check
### TEAM-M5: ADMIN bypass produces no audit trail
### TEAM-M6: Team deletion RESTRICT FK causes 500
### TEAM-M7: No rate limit on invite/join-request creation

---

## Low

### TEAM-L1: Redundant cascade deletes
### TEAM-L2: Username enumeration via invite flow
### TEAM-L3: Pending join request not cancelled on invite accept
