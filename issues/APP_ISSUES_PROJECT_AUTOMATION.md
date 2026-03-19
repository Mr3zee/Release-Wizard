# ProjectAutomation Issues

**Screen:** ProjectAutomation (`/projects/{id}/automation`)
**File:** `composeApp/src/commonMain/kotlin/com/github/mr3zee/automation/ProjectAutomationScreen.kt`

---

## 2C-1: Maven form Create button hidden below viewport [HIGH]
- **Severity:** High
- **Location:** Lines 637-712, `CreateMavenTriggerInlineForm`
- **Problem:** When the Maven Publication Trigger inline form opens, the "Include SNAPSHOT versions" checkbox and Create button are clipped below the viewport. The page uses `verticalScroll` but doesn't auto-scroll when the form opens.
- **Fix:** Add a `LaunchedEffect` that scrolls the parent scroll state to the bottom when the Maven form becomes visible, or use `bringIntoViewRequester` on the Create button.

## 2C-2: Webhook secret field not visible in secret card [HIGH]
- **Severity:** High
- **Location:** Lines 416-483, `WebhookSecretInlineCard`
- **Problem:** In the live screenshot, the secret value text field between the title and warning text is not visible — the card shows blank space. The `RwTextField` (read-only) and copy button exist in code but render invisible or empty.
- **Fix:** Debug whether the `secret` value is populated when `webhookCreated` fires. Ensure the text field renders with a visible value. Check that the copy icon button has adequate contrast against `tertiaryContainer`.

## 2C-3: Webhook URL not copyable
- **Severity:** Medium
- **Location:** Lines 762-801, `WebhookTriggerItem`
- **Problem:** Full webhook URL displayed as `Text(maxLines = 1, caption style)` but no way to copy it. Users need this URL for CI/CD configuration.
- **Fix:** Add a copy-to-clipboard icon button next to the URL, following the pattern in `WebhookSecretInlineCard`.

## 2C-4: No max-width constraint on content area
- **Severity:** Medium
- **Location:** Lines 189-194
- **Problem:** Content column uses `fillMaxSize()` with only `Spacing.lg` (16dp) padding. On wide displays, fields stretch uncomfortably wide.
- **Fix:** Add `Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter)`.

## 2C-5: Cron expression uses body style instead of monospace
- **Severity:** Medium
- **Location:** Line 729, `ScheduleItem`
- **Problem:** Cron expression "0 9 * * *" uses `AppTypography.body` (14sp Normal) instead of monospace. Cron expressions are code tokens.
- **Fix:** Change to `AppTypography.code` (13sp Monospace).

## 2C-6: Preset selector uses invisible click overlay
- **Severity:** Medium
- **Location:** Lines 536-573, `CreateScheduleInlineForm`
- **Problem:** `RwTextField(readOnly)` overlaid with transparent clickable `Box(Modifier.matchParentSize().clickable{...})`. Swallows focus events, screen readers can't identify it as a dropdown.
- **Fix:** Add `Role.DropdownList` semantics to the clickable overlay and a tooltip to the trailing dropdown icon.

## 2C-7: Inconsistent spacing between toggle and delete icon
- **Severity:** Medium
- **Location:** Lines 722-758, 767-801, 809-857 (all trigger items)
- **Problem:** `RwSwitch` and delete `RwIconButton` in `Arrangement.SpaceBetween` row with no explicit spacing between them.
- **Fix:** Wrap switch and delete button in `Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm))`.

## 2C-8: Empty state text lacks visual distinction
- **Severity:** Low
- **Location:** Lines 215-228 (schedules), 273-286 (webhooks), 331-344 (maven)
- **Problem:** Each section's empty state is just two lines of `onSurfaceVariant` text with no icon or centering.
- **Fix:** Add a small illustrative icon above the empty state text; center horizontally.

## 2C-9: Section spacing too tight (4dp)
- **Severity:** Low
- **Location:** Line 385, `AutomationSection`
- **Problem:** `Arrangement.spacedBy(Spacing.xs)` (4dp) between section heading and content is cramped for 18sp headings.
- **Fix:** Increase to `Spacing.sm` (8dp).

## 2C-10: Webhook URL text truncation without ellipsis
- **Severity:** Low
- **Location:** Line 779
- **Problem:** `maxLines = 1` but no `overflow = TextOverflow.Ellipsis` — text silently clipped.
- **Fix:** Add `overflow = TextOverflow.Ellipsis`.
