# ProjectEditor Issues

**Screen:** ProjectEditor (DAG editor, `/projects/new/edit` or `/projects/{id}/edit`)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/editor/DagEditorScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/editor/DagCanvas.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/editor/DagEditorViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/editor/BlockPropertiesPanel.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/editor/TemplateAutocompleteField.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/editor/DagCanvasDrawing.kt`

---

## 2B-1: TemplateAutocompleteField uses raw OutlinedTextField [HIGH]
- **Severity:** High
- **File:** `TemplateAutocompleteField.kt`, line 126
- **Problem:** Uses Material3 `OutlinedTextField` directly instead of custom `RwTextField`. Creates visual inconsistency: different border styling, label animation, and padding from nearby fields.
- **Fix:** Replace `OutlinedTextField` with `RwTextField`, adapting the autocomplete dropdown overlay to work with the custom component.

## 2B-2: Sidebar toggle buttons only 24dp [HIGH]
- **Severity:** High
- **File:** `DagEditorScreen.kt`, lines 378-391, 438-451
- **Problem:** Toggle buttons are `24.dp` with `16.dp` icons — well below 44dp minimum click target. Very difficult to discover and click.
- **Fix:** Increase button size to at least `44.dp` with `24.dp` icons. Consider a more prominent toggle affordance.

## 2B-3: White text on light block colors fails WCAG contrast
- **Severity:** Medium
- **File:** `DagCanvasDrawing.kt`, block color definitions
- **Problem:** White text (`blockText = Color.White`) on pastel block colors:
  - GitHub Publication `#34D399` (light green): ~2.5:1 contrast
  - Container `#94A3B8` (light gray): ~2.8:1 contrast
  - TeamCity Build `#A78BFA` (light purple): ~3.2:1 contrast
  - WCAG AA requires 4.5:1
- **Fix:** Either darken block fill colors or use dark text (`Color.Black` or `Color(0xFF1E1E1E)`) on light blocks. Compute luminance to decide text color dynamically.

## 2B-4: "Fit" button doesn't do true zoom-to-fit
- **Severity:** Medium
- **File:** `DagCanvas.kt`, lines 366-370
- **Problem:** Resets `zoom = 1f` and `panOffset = Offset.Zero` instead of computing zoom/pan to contain all blocks.
- **Fix:** Calculate bounding box of all blocks, compute zoom level that fits bounds within canvas size, and center the view.

## 2B-5: Blocks placed off-screen with no auto-pan
- **Severity:** Medium
- **File:** `DagEditorViewModel.kt`, lines 646-656 (`nextPlacementPosition()`)
- **Problem:** New blocks placed at 220dp intervals to the right, quickly going off-screen (visible canvas ~400dp with both sidebars open). No auto-pan or auto-zoom.
- **Fix:** After placing a new block, auto-pan the canvas to center on the new block, or call zoom-to-fit.

## 2B-6: Zoom controls lack background
- **Severity:** Medium
- **File:** `DagCanvas.kt`, zoom controls overlay
- **Problem:** Zoom percentage and +/- buttons drawn directly over canvas grid with no background container — hard to read depending on pan position.
- **Fix:** Add a `Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(8.dp))` or semi-transparent background behind zoom controls.

## 2B-7: Canvas hint disappears too soon
- **Severity:** Medium
- **File:** `DagCanvas.kt`, lines 313-325
- **Problem:** Hint "Drag from output port to input port to connect blocks" shown only when `graph.blocks.size <= 1`. Adding a 2nd block removes it before any connection is made.
- **Fix:** Change condition to `graph.edges.isEmpty()` (persist until first edge is created).

## 2B-8: No visual feedback when block is added
- **Severity:** Medium
- **Problem:** Clicking a block type in toolbar silently adds a block at computed position. No animation, scroll, or flash to draw attention. If off-screen, user may not know it was created.
- **Fix:** After adding a block, auto-pan to it and briefly highlight it (e.g., flash the border or use a scale-in animation).

## 2B-9: Edge selection has no hover affordance
- **Severity:** Medium
- **Problem:** Users can click near a bezier edge to select it, but there's no hover cursor change or visual highlight before clicking. Hit threshold is `8f / zoom` logical pixels.
- **Fix:** On hover proximity to an edge, highlight it (change color or increase stroke width) and change cursor to pointer.

## 2B-10: Right sidebar toggle partially clipped when collapsed
- **Severity:** Medium
- **File:** `DagEditorScreen.kt`
- **Problem:** When right sidebar is collapsed, toggle button appears at very edge of viewport, partially overlapping canvas border.
- **Fix:** Adjust positioning to ensure toggle button is fully visible.

## 2B-11: Dirty indicator "*" uses error color
- **Severity:** Low
- **File:** `DagEditorScreen.kt`, line 149
- **Problem:** Unsaved changes indicator uses `MaterialTheme.colorScheme.error` (red). Red for a normal state creates false alarm.
- **Fix:** Use `onSurfaceVariant` or a subtle amber/warning color.

## 2B-12: Properties panel empty state lacks visual weight
- **Severity:** Low
- **File:** `BlockPropertiesPanel.kt`, lines 71-78
- **Problem:** "Select a block to edit its properties" in `bodySmall` with no icon. Other screens have richer empty states.
- **Fix:** Add an icon (e.g., `Icons.Outlined.TouchApp`) above the text.
