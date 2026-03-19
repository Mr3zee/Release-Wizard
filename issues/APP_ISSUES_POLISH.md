# Polish & Low-Priority Issues

Issues that can be addressed after the high/medium priority fixes.

---

## 3A: Animation & Transitions

### 3A-1: LoginScreen card height jumps on mode toggle
- **Screen:** LoginScreen
- **Fix:** `AnimatedVisibility` with vertical expand for confirm password section

### 3A-2: MyInvites no animation on card removal
- **Screen:** MyInvites
- **Fix:** Add `animateItem()` to LazyColumn items

### 3A-3: Save button style abrupt transition (Ghost→Primary)
- **Screen:** ProjectEditor, `DagEditorScreen.kt` lines 176-183
- **Fix:** Use outlined style when clean, filled when dirty, for a smoother transition

---

## 3B: Tooltips & Accessibility Completions

### 3B-1: Password visibility toggle lacks tooltip
- **Screen:** LoginScreen, lines 132-141
- **Fix:** Wrap with `RwTooltip`

### 3B-2: Toolbar buttons lack tooltips
- **Screen:** TeamDetail, lines 66-92
- **Fix:** Wrap Back, Audit Log, Manage, Leave with `RwTooltip`

### 3B-3: Section icons lack contentDescription
- **Screen:** ProjectAutomation, lines 203, 261, 319
- **Fix:** Add descriptions

### 3B-4: Checkbox state not announced to screen readers
- **Screen:** ProjectAutomation, lines 701-710
- **Fix:** Add `stateDescription` semantics to row

### 3B-5: Warning icon missing contentDescription
- **Screen:** TeamDetail, line 89
- **Fix:** Add `contentDescription = "Warning"`

### 3B-6: Error banner dismiss lacks tooltip
- **Screen:** ConnectionForm, lines 236-245
- **Fix:** Add `RwTooltip`

---

## 3C: Minor Spacing & Padding

### 3C-1: LazyColumn missing bottom contentPadding
- **Screens:** AuditLog (line 135), TeamDetail
- **Fix:** Add `contentPadding = PaddingValues(bottom = Spacing.xl)`

### 3C-2: Filter chip spacer too small (4dp)
- **Screen:** ConnectionList, line 219
- **Fix:** Increase to `Spacing.sm` (8dp)

### 3C-3: Outputs 32dp double-indent
- **Screen:** ReleaseView, lines 536-537
- **Fix:** Remove extra `padding(start = Spacing.lg)`

### 3C-4: Section heading padding inconsistency
- **Screen:** TeamManage, lines 153, 217, 268, 305
- **Fix:** Standardize to `padding(horizontal = Spacing.lg, vertical = Spacing.sm)`

### 3C-5: HorizontalDivider placement in Automation
- **Screen:** ProjectAutomation, lines 253, 311
- **Fix:** Align with content padding

---

## 3D: Typography Refinements

### 3D-1: Login error text uses body (14sp) — too large
- **Screen:** LoginScreen, line 205
- **Fix:** Use `AppTypography.bodySmall` or `AppTypography.caption`

### 3D-2: Login subtitle text borderline contrast
- **Screen:** LoginScreen, line 100
- **Fix:** Increase color to `~#B0B8C4` for ~6:1 contrast

### 3D-3: SubBuild status icons use Unicode
- **Screen:** ReleaseView, lines 181-188
- **Fix:** Replace with Material icons

### 3D-4: TopAppBar title missing maxLines/overflow
- **Screen:** TeamList, line 102
- **Fix:** Add `maxLines = 1, overflow = TextOverflow.Ellipsis`

---

## 3E: Information Density

### 3E-1: Missing invite timestamp on cards
- **Screen:** MyInvites
- **Fix:** Show "Invited X days ago" from `createdAt`

### 3E-2: No count indicator for releases
- **Screen:** ReleaseList
- **Fix:** Add "Showing X of Y" below filters

### 3E-3: No sort controls
- **Screens:** ProjectList, ConnectionList
- **Fix:** Add sort dropdown (name, date)

### 3E-4: Missing metadata on project cards
- **Screen:** ProjectList
- **Fix:** Add "Last edited X ago" or block count with more detail
