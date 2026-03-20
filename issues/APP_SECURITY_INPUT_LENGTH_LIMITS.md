# APP_SECURITY_INPUT_LENGTH_LIMITS

## Severity: Low
## Category: Input Validation
## Affected Screens: All screens with text input

### Description

Most text input fields lack both client-side `maxLength` constraints and server-side length validation:

- **Team name/description** — server validates 100/2000 chars but client has no limit hints
- **Project name** — no server validation; only DB `varchar(255)` limit
- **Connection name** — `varchar(255)` DB limit only
- **Secret fields** (tokens, URLs) — stored in `text` column with no size limit
- **Search queries** — no length limit; sent as URL query parameters
- **Block names** — no limit anywhere
- **Invite username** — no length limit
- **DAG block parameters** — not validated (unlike release parameters)

### Impact

Extremely long inputs waste bandwidth, consume DB storage, and could cause rendering issues. The `RequestSizeLimit` plugin (1MB) provides a ceiling but is generous for most fields.

### Affected Locations

- All `RwTextField` usages in `composeApp/` — no `maxLength` set
- All service `create`/`update` methods in `server/` — inconsistent length validation

### Recommendation

Add consistent length validation: client-side `maxLength` on text fields, server-side `require(field.length <= limit)` in service methods. Match DB column sizes.
