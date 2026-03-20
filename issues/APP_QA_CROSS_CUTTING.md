# APP_QA_CROSS_CUTTING — Cross-Cutting & Shared Component Test Coverage Gaps

## Scope: Navigation, shared components, integration between screens
**Existing test files:** AppNavigationTest, NavigationControllerTest, ScreenRoutesTest, Phase1/3 tests, etc.

---

## HIGH Priority

### QA-CROSS-1: Sidebar nav items never clicked in any test
All section switches in `AppNavigationTest` are driven via `navController.navigateToSection()` directly, bypassing the rendered `SidebarNavItem` buttons. No test clicks `sidebar_nav_releases`, `sidebar_nav_connections`, or `sidebar_nav_teams`.

### QA-CROSS-2: AppShell sidebar collapse/expand untested
`sidebar_collapse_toggle`, auto-collapse threshold at 900dp, collapsed vs expanded rendering — none exercised.

### QA-CROSS-3: Sign-out two-click confirmation flow untested
`SignOutItem` has a two-click confirmation (`confirmPending` → 3-second auto-dismiss). No test clicks `sidebar_sign_out` once, verifies the confirmation label changes, then confirms.

### QA-CROSS-4: Session expiry mid-navigation → login redirect
`AuthEventBus` emits `SessionExpired` on 401 (unit-tested). But the full loop (authenticated → 401 → `AuthEventBus` fires → user lands on `LoginScreen`) is never integration-tested.

### QA-CROSS-5: Team switcher interaction untested
`SidebarTeamSwitcher` and `TeamDropdown` — no test clicks `sidebar_team_switcher`, opens the dropdown, selects a team, and verifies `onTeamChanged` fires.

---

## MEDIUM Priority

### QA-CROSS-6: RwInlineConfirmation debounce logic
The confirm button is deliberately disabled for 300ms after appearing. No test verifies the button is initially disabled then becomes enabled.

### QA-CROSS-7: RwInlineForm keyboard shortcuts (Escape/Enter)
Escape-to-dismiss and Enter-to-submit keyboard paths are not tested.

### QA-CROSS-8: Settings panel (theme and language) in sidebar
`SidebarSettingsContent` — theme cycling, language picker, and shortcuts trigger behind `sidebar_settings`. All untested.

### QA-CROSS-9: Team section drill-down via AppNavigation
Navigation to `TeamDetail`, `TeamManage`, `MyInvites`, and `AuditLog` screens is never exercised through the full composable navigation system.

### QA-CROSS-10: RwSwitch, RwChip, RwRadioButton — no tests
These shared components have no dedicated or indirect test coverage.

### QA-CROSS-11: Cross-screen data propagation under team switch
No test that creates a project then navigates to releases to verify the project appears in the release project picker. The `activeTeamId` StateFlow threading is untested.

### QA-CROSS-12: navigateFromExternal / URL-sync end-to-end
`PlatformRouter`'s URL-bar sync and `navigateFromExternal` triggered by external URL change are untested at the composable level.

---

## LOW Priority

### QA-CROSS-13: LoadMoreItem (pagination) — zero test scenarios
The sole pagination mechanism has no test with more than one page of results.

### QA-CROSS-14: ReleaseView deep link integration
No test exercises clicking a release item in `ReleaseListScreen` and verifying `ReleaseDetailScreen` loads.

### QA-CROSS-15: RefreshIconButton spinning state assertion
The `refresh_icon_spinning` tag is never asserted during in-flight refresh.

### QA-CROSS-16: ProjectAutomation navigation from AppNavigation
`Screen.ProjectAutomation` is never loaded through the navigation system in composable tests.
