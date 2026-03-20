# APP_SECURITY_TEMPLATE_EXPRESSION_INJECTION

## Severity: Medium
## Category: Injection
## Affected Screens: DagEditorScreen

### Description

Template expressions like `${param.KEY}` and `${block.ID.OUTPUT}` are stored in parameter values and gate messages. These are interpolated at runtime by the `TemplateEngine`. If a parameter key or block output name contains characters forming nested or recursive template expressions, the resolution may behave unexpectedly, potentially leaking values from other parameters or creating infinite-substitution patterns.

### Impact

A malicious user could craft parameter keys or values containing `${...}` expressions that reference other release parameters or block outputs in unexpected ways, potentially leaking sensitive values across block boundaries.

### Affected Locations

- `composeApp/.../editor/TemplateSuggestions.kt:49-57` — template expressions
- `shared/.../template/TemplateEngine.kt` — expression resolution

### Recommendation

1. Validate that parameter keys do not contain `$`, `{`, or `}` characters
2. Limit template expression nesting depth in the TemplateEngine
3. Sanitize or escape user input before template interpolation during execution
