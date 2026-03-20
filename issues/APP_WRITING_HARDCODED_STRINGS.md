# APP_WRITING_HARDCODED_STRINGS — Strings Bypassing Localization System

Several user-facing strings are hardcoded inline in Kotlin code instead of using
`packStringResource`, bypassing the localization/language-pack theming system.

## Issues

### EDITOR_01 — Hardcoded validation error strings (HIGH)
**File:** `composeApp/…/editor/DagEditorScreen.kt` (lines ~651-658)

Seven validation messages are inline string literals:
- `"Too many blocks: ${error.count} (max ${error.max})"`
- `"Too many edges: ${error.count} (max ${error.max})"`
- `"Nesting too deep: depth ${error.depth} (max ${error.max})"`
- `"Block name too long: ${error.length} chars (max ${error.max})"`
- `"Too many parameters on block (max ${error.max})"`
- `"Parameter key too long (max ${error.max})"`
- `"Parameter value too long (max ${error.max})"`

The first four validation types (cycle, duplicate ID, invalid edge, self-loop) correctly use
`packStringResource`. These seven do not.

**Fix:** Create string resources with format placeholders (e.g., `editor_validation_too_many_blocks`)
and add themed overrides to all 6 language packs.

### EDITOR_02 — Hardcoded default block names (HIGH)
**File:** `composeApp/…/editor/EditorToolbar.kt` (line ~90, ~196-201)

Container default name `"Container"` and action block defaults (`"Build"`, `"Action"`,
`"Publish"`, `"Notify"`) are hardcoded literals.

**Fix:** Extract to string resources.

### CONNFORM_02 — Hardcoded placeholder values in ConnectionForm (HIGH)
**File:** `composeApp/…/connections/ConnectionFormScreen.kt`

Eight placeholder values are hardcoded inline:
- `"https://hooks.slack.com/services/..."`
- `"https://teamcity.example.com"`
- `"eyJ0eXAi…"`
- `"30"` (twice)
- `"ghp_xxxx…"`
- `"my-org"`
- `"my-repo"`

**Fix:** Extract each into `strings.xml` (e.g., `connections_slack_webhook_placeholder`).
