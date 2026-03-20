# APP_SECURITY_MASKED_SECRET_OVERWRITE

## Severity: High
## Category: Secrets Management / Data Integrity
## Affected Screens: ConnectionFormScreen, ConnectionListScreen

### Description

When editing a connection, the server returns masked secrets (e.g., `"****abcd"`). The form populates secret fields (token, webhookUrl) with these masked values. If the user edits only non-secret fields (like the connection name) and saves, the masked string is submitted as the new config value.

The server's `updateConnection` does not detect that the submitted config contains a masked sentinel value — it overwrites the real encrypted secret in the database with the masked string.

### Impact

Permanent loss of the real secret. After saving any non-secret field, the connection's actual API token/webhook URL is destroyed and replaced with a masked placeholder, rendering the connection non-functional. This is both a data integrity bug and effectively a denial-of-service for any release pipeline depending on that connection.

### Affected Locations

- `server/.../connections/ConnectionsService.kt:87-105` — `updateConnection` does not detect masked values
- `server/.../connections/ConnectionsService.kt:175-177` — `mask()` function
- `composeApp/.../connections/ConnectionFormScreen.kt:114-136` — form populates with masked values

### Recommendation

Implement one of:
1. **Server-side sentinel detection**: if a config field matches the masked pattern (`****` prefix), merge the existing decrypted value for that field instead of overwriting.
2. **Client-side approach**: send `config: null` in the update request when the user has not modified secret fields, relying on the existing nullable config in `UpdateConnectionRequest`.
3. **Separate endpoint**: use a dedicated "update secrets" endpoint that requires re-entry of the full secret.
