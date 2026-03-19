# ConnectionList Issues

**Screen:** ConnectionList (`/connections`, Alt+3)
**File:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/connections/ConnectionListScreen.kt`

---

## 2F-1: Connection type displayed 3 times redundantly
- **Severity:** Medium
- **Location:** Lines 330-360, `ConnectionListItem`
- **Problem:** Each list item shows the type as: (1) subtitle text below name, (2) colored badge pill, (3) implicitly via badge color. Visually noisy.
- **Fix:** Remove subtitle type text. Keep badge only. Replace subtitle with useful metadata (owner/repo for GitHub, server URL for TeamCity, "Last updated" timestamp).

## 2F-2: Missing metadata/timestamps in list items
- **Severity:** Medium
- **Location:** Lines 330-352
- **Problem:** Items only show name + type (redundantly) + optional webhook URL. `Connection` model has `createdAt`/`updatedAt` but they're not displayed.
- **Fix:** Show relevant metadata: "testowner/testrepo" for GitHub, "teamcity.example.com" for TeamCity, creation date for all.

## 2F-3: No "Edit" button — clickable card not obvious
- **Severity:** Medium
- **Location:** Lines 281-303
- **Problem:** "Test" and "Delete" are visible buttons, but editing requires clicking the card body. No edit icon or chevron affordance.
- **Fix:** Add a chevron (`Icons.AutoMirrored.Filled.KeyboardArrowRight`) like ProjectList, or an explicit "Edit" ghost button.

## 2F-4: Delete button always visible in bright red
- **Severity:** Medium
- **Location:** Lines 377-384
- **Problem:** `RwButtonVariant.Danger` (bright red background) on every item creates visual noise and draws eye away from content.
- **Fix:** Move Delete behind an overflow/more menu (three-dot icon), or make it a ghost variant with danger styling only on hover.

## 2F-5: Webhook URL contrast marginal
- **Severity:** Low
- **Location:** Lines 342-351
- **Problem:** `AppTypography.bodySmall` with `onSurfaceVariant` — marginal contrast for small text.
- **Fix:** Use `AppTypography.code` (monospace) for URL text to improve readability and type distinction.
