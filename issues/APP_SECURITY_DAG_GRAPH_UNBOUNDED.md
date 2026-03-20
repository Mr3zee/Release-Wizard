# APP_SECURITY_DAG_GRAPH_UNBOUNDED

## Severity: Medium
## Category: Input Validation / Denial of Service
## Affected Screens: DagEditorScreen

### Description

The DAG graph validation (`validateDagGraph`) checks structural issues (cycles, self-loops, duplicate IDs) but enforces no limits on:

1. **Total number of blocks** — no cap, could be thousands
2. **Total number of edges** — no cap
3. **Container nesting depth** — `DagValidator.validate()` recurses without depth limit, risking stack overflow
4. **Block name length** — no length constraint on either client or server
5. **Parameter key/value lengths within blocks** — not validated (unlike release parameters which enforce MAX_PARAM_KEY_LENGTH/MAX_PARAM_VALUE_LENGTH)

The entire DagGraph is serialized as JSON into a single database column.

### Impact

A malicious or buggy client could submit a DAG with thousands of blocks/edges, deeply nested containers, or blocks with megabyte-long names/parameters. This is a stored DoS since all team members loading the project trigger the same expensive deserialization and rendering.

### Affected Locations

- `server/.../projects/ProjectsService.kt:133-146` — `validateDagGraph` no size limits
- `composeApp/.../editor/BlockPropertiesPanel.kt:82-93` — no block name length limit
- `composeApp/.../editor/BlockPropertiesPanel.kt:403-413` — no parameter length limit

### Recommendation

Add limits in `validateDagGraph()`: max blocks (500), max edges (2000), max nesting depth (10), max parameters per block (50), max name length (255), max parameter key/value lengths matching existing constants.
