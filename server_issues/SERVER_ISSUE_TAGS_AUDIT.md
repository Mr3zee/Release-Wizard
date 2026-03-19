# Tags & Audit Issues

15 findings: 2 Critical, 4 High, 6 Medium, 3 Low

## Critical

### ✅ TAG-C1: Tag rename/delete in TeamRoutes bypass TagService entirely

**Files:** `TeamRoutes.kt:168, 178`

Team-scoped routes call `tagRepository` directly, skipping service validation and audit.

**Fix:** Inject `TagService` instead of `TagRepository`.

---

### ✅ TAG-C2: No audit events for any tag mutation

**Files:** `TagRoutes.kt`, `TeamRoutes.kt`, `TagService.kt`

`AuditAction.TAG_RENAMED` and `TAG_DELETED` exist but are never used.

---

## High

### TAG-H1: `renameTag` TOCTOU race — conflict-check and update not atomic

**Files:** `ExposedTagRepository.kt:78-91`

**Fix:** SERIALIZABLE isolation or single `INSERT ... ON CONFLICT`.

---

### TAG-H2: `setTagsForRelease` delete-then-insert without isolation

**Files:** `ExposedTagRepository.kt:36-46`

---

### TAG-H3: Cross-team IDOR in `renameTag` deduplication

**Files:** `ExposedTagRepository.kt:78-81`

Deduplication query doesn't filter by teamId.

**Fix:** Add teamId filter when `teamUuid != null`.

---

### TAG-H4: Tag names have no length or character validation

**Files:** `TagService.kt:29`, `ReleaseTagTable.kt:8`

**Fix:** Max length (100), character allowlist (`[a-z0-9_.-]`), max list size (20).

---

## Medium

### TAG-M1: Audit log injection via user-controlled `details` field
### TAG-M2: `AuditRepository.findByTeam` two queries without snapshot
### TAG-M3: `AuditService.log` silently swallows all failures
### TAG-M4: `AuditService` registered in `teamsModule` — scope leak
### TAG-M5: Audit log readable by any team member
### TAG-M6: Unknown enum values on read cause 500

---

## Low

### TAG-L1: `renameTag` count doesn't reflect deduplication deletes
### TAG-L2: `AuditEvent.id` passed as empty string
### TAG-L3: Missing composite index on `(teamId, tag)`
