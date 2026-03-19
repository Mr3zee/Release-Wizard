# ReleaseView Issues

**Screen:** ReleaseView (`/releases/{id}`)
**Files:**
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/releases/ReleaseDetailScreen.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/releases/ExecutionDagCanvas.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/releases/SubBuildsSection.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/releases/ErrorDetailSection.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/releases/ArtifactTreeView.kt`
- `composeApp/src/commonMain/kotlin/com/github/mr3zee/releases/ReleaseDetailViewModel.kt`

**Note:** This screen has the most issues (35) due to its complexity.

---

## High Priority

### 2E-1: TopAppBar title lacks release identification
- **Severity:** High
- **Location:** Lines 96-104
- **Problem:** Shows only static "Release Detail" + status badge. No project name, release name, version, or creation time visible.
- **Fix:** Show project name and release identifier (e.g., "MyPipeline — Release #3") in the title.

### 2E-2: Status icons disappear at low zoom
- **Severity:** High
- **Location:** `DagCanvasDrawing.kt`, line 424
- **Problem:** `drawBlockStatusIcon` returns early if `iconSize < 4f`. At low zoom, users lose status visibility.
- **Fix:** Add fallback: when icons too small, tint the block border or fill with the status color.

### 2E-3: Top bar actions change dramatically by status
- **Severity:** High
- **Problem:** RUNNING shows Stop+Cancel, STOPPED shows Resume+Cancel, PENDING shows Cancel, terminal shows Rerun+Archive. Button groups completely change.
- **Fix:** Use consistent positional slots. Always render primary action on the right, secondary/destructive on the left. Use disable/hide instead of remove.

## Medium Priority

### 2E-4: No pan/zoom visible controls
- **Severity:** Medium
- **Problem:** Canvas supports scroll-to-zoom and drag-to-pan, but no visible controls. Undiscoverable.
- **Fix:** Add zoom controls overlay (zoom %, +/- buttons, fit-to-screen button) matching the ProjectEditor canvas pattern.

### 2E-5: No release progress overview
- **Severity:** Medium
- **Problem:** No "3/8 blocks completed" summary or progress bar. Users must mentally aggregate block statuses.
- **Fix:** Add a progress indicator in the header area showing completed/total blocks.

### 2E-6: Two "Stop" buttons with unclear scope
- **Severity:** Medium
- **Problem:** Top bar "Stop Release" and block panel "Stop" both use red styling. Scope distinction unclear.
- **Fix:** Differentiate labels: "Stop Release" (top) vs "Stop Block: {blockName}" (panel).

### 2E-7: DAG blocks no hover cursor change
- **Severity:** Medium
- **Problem:** Blocks are clickable via `pointerInput` but cursor doesn't change to pointer on hover.
- **Fix:** Add `Modifier.pointerHoverIcon(PointerIcon.Hand)` or cursor change on block hover detection.

### 2E-8: Loading state bare spinner
- **Severity:** Medium
- **Location:** Lines 196-206
- **Problem:** Centered `CircularProgressIndicator` with no text.
- **Fix:** Add "Loading release..." text below spinner.

### 2E-9: Disconnected indicator low contrast
- **Severity:** Medium
- **Location:** Lines 119-126
- **Problem:** Red text at caption size — hard to read and critical connectivity feedback.
- **Fix:** Use `AppTypography.body` with error color for better visibility.

### 2E-10: Block detail panel max 350dp hard-coded
- **Severity:** Medium
- **Location:** Lines 371-374
- **Problem:** 350dp cap may be too small for complex blocks, or too large on small screens.
- **Fix:** Use `fillMaxHeight(fraction = 0.4f)` or responsive calculation.

### 2E-11: Block detail panel full-width
- **Severity:** Medium
- **Location:** Line 367
- **Problem:** Panel stretches full width — fatiguing on wide displays.
- **Fix:** Add `widthIn(max = 800.dp)` and center.

### 2E-12: Outputs double-indent (32dp)
- **Severity:** Medium
- **Location:** Lines 536-537
- **Problem:** Output key-value pairs indented 16dp inside a panel with 16dp padding = 32dp total left margin.
- **Fix:** Remove the extra `padding(start = Spacing.lg)` on output entries.

### 2E-13: SubBuildsSection no ripple on expand/collapse
- **Severity:** Medium
- **Location:** `SubBuildsSection.kt`, line 56
- **Problem:** `clickable(indication = null)` removes ripple feedback — header doesn't look interactive.
- **Fix:** Remove `indication = null` to restore default ripple.

### 2E-14: No release progress bar
- **Severity:** Medium
- **Problem:** No overall "X/Y blocks completed" indicator in the header.
- **Fix:** Add `LinearProgressIndicator` or text showing completion fraction.

## Low Priority

### 2E-15: Block outputs no copy-to-clipboard
- **Location:** Lines 525-538
- **Fix:** Add copy button per output entry (ErrorDetailSection already has this pattern).

### 2E-16: No empty state when DAG has no blocks
- **Fix:** Add "No blocks in this release" message.

### 2E-17: "Close" button is text-only ghost
- **Fix:** Add X icon button in top-right of panel.

### 2E-18: SubBuild status icons use raw Unicode
- **Location:** Lines 181-188
- **Fix:** Replace with Material icons.

### 2E-19: Block without execution — minimal panel not differentiated
- **Fix:** Add visual differentiation (lighter tonal elevation, "Waiting" icon).

### 2E-20: Canvas blocks fixed 180x70 — long names clipped
- **Fix:** Add text ellipsis to `drawText` with width constraint.

### 2E-21: Artifact tree view no search/filter
- **Fix:** Add search field for large artifact trees.

### 2E-22: No navigation from block detail to connection/configuration
- **Fix:** Add "View Configuration" link in block detail panel.

### 2E-23: Approval progress LinearProgressIndicator without label
- **Fix:** Add accessibility label connecting progress bar to text.

### 2E-24: Gate phase context text not actionable
- **Fix:** Add brief explanation of what pre/post-execution gates mean.

### 2E-25: Block type label text-only without icon
- **Fix:** Add small type icon next to label for scannability.
