# Projects Issues

12 findings: 2 Critical, 4 High, 4 Medium, 2 Low

## Critical

### ✅ PROJ-C1: Lock check in `updateProject` not atomic — TOCTOU race

**Files:** `ProjectsService.kt:72-76`, `ExposedProjectsRepository.kt:162-178`

`findActiveLock()` and `repository.update()` are separate transactions.

**Fix:** Single transaction for lock read + project update.

---

### ✅ PROJ-C2: `tryAcquire` delete-then-insert on expired lock not atomic

**Files:** `ExposedProjectLockRepository.kt:63-87`

PK constraint catch is the safety net, not the intended design.

**Fix:** Use upsert: `INSERT ... ON CONFLICT (project_id) DO UPDATE`.

---

## High

### ✅ PROJ-H1: `validateConnectionTeamConsistency` not called on `updateProject`

**Files:** `ProjectsService.kt:69-88`

PUT can silently inject cross-team connection IDs.

---

### ✅ PROJ-H2: No server-side DAG validation on create or update

**Files:** `ProjectsService.kt`, `DagValidator.kt`

`DagValidator.validate()` exists but is never called. Cyclic DAGs, dangling edges possible.

**Fix:** Call `DagValidator.validate(request.dagGraph)` in both create and update.

---

### ✅ PROJ-H3: `findAllWithCount` two separate queries without snapshot

**Files:** `ExposedProjectsRepository.kt:84-103`

**Fix:** Use `COUNT(*) OVER()` window function.

---

### ✅ PROJ-H4: Blank-name validation in route only — bypassed by internal callers

**Files:** `ProjectsRoutes.kt:33-36`

**Fix:** Move validation to service layer.

---

## Medium

### PROJ-M1: Lock bypass — write proceeds when no lock exists
### ✅ PROJ-M3: `updateProject` emits no audit event
### PROJ-M4: `teamIds` silently drops invalid UUIDs

---

## Low

### PROJ-L1: `requireProjectId` parses UUID twice
### PROJ-L2: Request size limit bypassed by chunked encoding
