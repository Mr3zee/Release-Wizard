# ConnectionForm Issues

**Screen:** ConnectionForm (`/connections/new` or `/connections/{id}/edit`)
**File:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/connections/ConnectionFormScreen.kt`

---

## 2G-1: No inline validation or field-level error indicators [HIGH]
- **Severity:** High
- **Location:** Lines 159-167 (`handleSave`), 514-518 (`isValid`)
- **Problem:** Save button silently disabled when fields are empty. No asterisks on required fields, no inline error messages, no border color changes. Users don't know what needs to be filled.
- **Fix:**
  1. Mark required fields with asterisk or "Required" text
  2. Add `isError` state on `RwTextField` when user blurs an empty required field
  3. Consider a summary error banner on invalid Save attempt

## 2G-2: No "Test Connection" button on form
- **Severity:** Medium
- **Problem:** Users must save first, then test from the list — disruptive workflow.
- **Fix:** Add "Test Connection" button below the configuration fields. Validate connection without saving.

## 2G-3: No field descriptions/helper text
- **Severity:** Medium
- **Location:** Lines 265-477
- **Problem:** Only polling interval has supporting text. Other fields lack guidance.
- **Fix:** Add supporting text:
  - PAT: "Generate at github.com/settings/tokens"
  - Owner: "GitHub organization or user name"
  - Repository: "Repository name (not the full URL)"
  - Server URL: "e.g., https://teamcity.example.com"
  - Webhook URL: "Incoming Webhook URL from Slack app settings"

## 2G-4: Type selector accessibility
- **Severity:** Medium
- **Location:** Lines 296-303
- **Problem:** Invisible `Box` overlay for click handling — screen readers can't identify as dropdown.
- **Fix:** Add `Role.DropdownList` semantics to the clickable overlay.

## 2G-5: Placeholder text duplicates label
- **Severity:** Medium
- **Location:** Lines 265-274 and others
- **Problem:** `label = "Connection Name"` and `placeholder = "Connection Name"` — redundant.
- **Fix:** Use example values: placeholder "e.g., Production GitHub", "https://hooks.slack.com/...", etc.

## 2G-6: Section header lacks visual weight
- **Severity:** Medium
- **Location:** Line 340
- **Problem:** "GitHub Configuration" uses `AppTypography.subheading` (15sp Medium) — similar weight to field labels.
- **Fix:** Use `AppTypography.heading` (18sp SemiBold) for section headers.

## 2G-7: Missing top padding between TopAppBar and form
- **Severity:** Medium
- **Location:** Lines 257-263
- **Problem:** First field starts immediately below app bar with minimal breathing room.
- **Fix:** Add `Spacing.xl` (20dp) or `Spacing.xxl` (24dp) top padding.

## 2G-8: Polling interval empty string defaults silently to 30
- **Severity:** Medium
- **Location:** Lines 403-418, 461-477, 504/510
- **Problem:** When user clears the field, `toIntOrNull()` falls back to 30 silently. Field shows empty but value is 30.
- **Fix:** Show validation message "Using default: 30 seconds" when field is empty, or restore "30" in the field.
