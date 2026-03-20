# APP_WRITING_PLACEHOLDERS — Redundant or Unhelpful Placeholder Text

Multiple form fields use the same string for both `label` and `placeholder`,
wasting the placeholder's opportunity to provide examples or hints.

## Issues

### LOGIN_06 — Username and password placeholders repeat labels (LOW)
**Current:** Label = `"Username"`, Placeholder = `"Username"` (identical for all fields).
**Fix:** Set placeholders to null (label is sufficient) or provide examples:
username = `"e.g., jane.doe"`.

### PROJLIST_07 — Project name field (LOW)
**Current:** Label = `"Project name"`, Placeholder = `"Project name"`.
**Fix:** Placeholder = `"e.g., My Library v2.0"`.

### TEAMLIST_07 — Team name and description fields (LOW)
**Current:** Label = `"Team name"`, Placeholder = `"Team name"`.
Description: Label = `"Description (optional)"`, Placeholder = `"Description (optional)"`.
**Fix:** Team name placeholder = `"e.g., Android Core Team"`.
Description placeholder = `"e.g., Manages Android library releases"`.

### CONNFORM_10 — TeamCity token placeholder looks like a real JWT (LOW)
**Current:** `"eyJ0eXAi…"`
**Problem:** Looks like a real token prefix. May confuse users with different token formats.
**Fix:** `"Paste your TeamCity access token"` or remove the placeholder.

### CONNFORM_08 — "Owner" field is ambiguous (MEDIUM)
**Current:** Label = `"Owner"`, Placeholder = `"my-org"`.
**Problem:** "Owner" could mean repo owner, org name, or username.
**Fix:** Add supporting text: `"GitHub username or organization name"`.

### CONNFORM_04 — Credential fields lack setup guidance (MEDIUM)
No field explains where to obtain the credential.
**Fix:** Add supporting text:
- GitHub PAT: `"Generate at GitHub > Settings > Developer settings > Tokens"`
- TeamCity Token: `"Create at TeamCity > My Settings & Tools > Access Tokens"`
- Slack Webhook URL: `"Create at api.slack.com > Your Apps > Incoming Webhooks"`

### CONNFORM_01 — No required-field indicators (HIGH)
Save button silently disables with no explanation of which fields are missing.
No "*" markers, no inline validation, no "Required" hints.
**Fix:** Add required indicators to mandatory field labels and inline validation
messages when a required field is blank after interaction.
