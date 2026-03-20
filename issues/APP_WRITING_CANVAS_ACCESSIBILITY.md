# APP_WRITING_CANVAS_ACCESSIBILITY — DAG Canvas Invisible to Screen Readers

## Issues

### RELDETAIL_06 — Execution DAG canvas has no accessible content (MEDIUM)

The entire DAG visualization on the ReleaseDetailScreen — the primary UI element — is
a raw `Canvas` with `testTag("execution_dag_canvas")` but no `contentDescription` or
semantic annotations. Blocks are drawn as canvas shapes with no semantic tree entries.

**Impact:** Screen reader users cannot:
- Discover which blocks exist
- Know block statuses
- Navigate between blocks
- Select a block to see its details

The block detail panel partially compensates when a block is selected, but there is no
way to select a block without the visual canvas.

### RELLIST_05 — Release list items lack semantic grouping (HIGH)

The `ListItemCard` in ReleaseListScreen has no `semantics(mergeDescendants = true)`
annotation. Screen readers read text fragments individually instead of a grouped
announcement like `"MyProject — Running, started 2025-03-20 14:30"`.

### RELDETAIL_11 — Sub-build status icons use raw enum names (LOW)

Content descriptions derived from `subBuild.status.name.lowercase()` instead of
localized string resources. Will not translate.
**Fix:** Create localized resources for sub-build statuses.

## Fix

For the DAG canvas:
1. Add a semantic overlay using `Modifier.semantics` or invisible `Box` composables
   positioned over each block providing:
   - Content description per block (e.g., `"Block: Deploy to Staging — Running"`)
   - Click actions to select a block
   - Overall DAG state description (e.g., `"Release pipeline: 3 of 5 blocks completed"`)

For the release list:
- Add `Modifier.semantics(mergeDescendants = true) { contentDescription = "$title, ${status}" }`
  to the `ListItemCard`.
