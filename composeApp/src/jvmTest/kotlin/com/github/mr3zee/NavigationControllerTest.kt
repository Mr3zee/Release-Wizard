package com.github.mr3zee

import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.navigation.NavSection
import com.github.mr3zee.navigation.NavigationController
import com.github.mr3zee.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationControllerTest {

    // --- navigateToSection ---

    @Test
    fun `navigateToSection PROJECTS sets stack to ProjectList only`() {
        val nav = NavigationController()
        nav.navigateToSection(NavSection.PROJECTS)
        assertEquals(listOf(Screen.ProjectList), nav.backStack)
    }

    @Test
    fun `navigateToSection RELEASES sets stack to ProjectList then ReleaseList`() {
        val nav = NavigationController()
        nav.navigateToSection(NavSection.RELEASES)
        assertEquals(listOf(Screen.ProjectList, Screen.ReleaseList), nav.backStack)
    }

    @Test
    fun `navigateToSection CONNECTIONS sets stack to ProjectList then ConnectionList`() {
        val nav = NavigationController()
        nav.navigateToSection(NavSection.CONNECTIONS)
        assertEquals(listOf(Screen.ProjectList, Screen.ConnectionList), nav.backStack)
    }

    @Test
    fun `navigateToSection TEAMS sets stack to ProjectList then TeamList`() {
        val nav = NavigationController()
        nav.navigateToSection(NavSection.TEAMS)
        assertEquals(listOf(Screen.ProjectList, Screen.TeamList), nav.backStack)
    }

    @Test
    fun `navigateToSection from deep stack replaces entire stack`() {
        val nav = NavigationController()
        // Build up a deep stack of 5 items
        nav.navigate(Screen.ReleaseList)
        nav.navigate(Screen.ConnectionList)
        nav.navigate(Screen.TeamList)
        nav.navigate(Screen.TeamDetail(TeamId("t1")))
        assertEquals(5, nav.backStack.size)

        nav.navigateToSection(NavSection.RELEASES)
        assertEquals(listOf(Screen.ProjectList, Screen.ReleaseList), nav.backStack)
    }

    @Test
    fun `navigateToSection called twice with same section does not duplicate`() {
        val nav = NavigationController()
        nav.navigateToSection(NavSection.TEAMS)
        nav.navigateToSection(NavSection.TEAMS)
        assertEquals(listOf(Screen.ProjectList, Screen.TeamList), nav.backStack)
    }

    // --- navigateFromExternal ---

    @Test
    fun `navigateFromExternal with top-level ReleaseList sets ProjectList then ReleaseList`() {
        val nav = NavigationController()
        nav.navigateFromExternal(Screen.ReleaseList)
        assertEquals(listOf(Screen.ProjectList, Screen.ReleaseList), nav.backStack)
    }

    @Test
    fun `navigateFromExternal with detail TeamDetail reconstructs parent stack`() {
        val nav = NavigationController()
        val detail = Screen.TeamDetail(TeamId("t1"))
        nav.navigateFromExternal(detail)
        assertEquals(listOf(Screen.ProjectList, Screen.TeamList, detail), nav.backStack)
    }

    @Test
    fun `navigateFromExternal with ProjectList sets stack to ProjectList only`() {
        val nav = NavigationController()
        // Start from a non-default state
        nav.navigate(Screen.ReleaseList)
        nav.navigateFromExternal(Screen.ProjectList)
        assertEquals(listOf(Screen.ProjectList), nav.backStack)
    }

    @Test
    fun `navigateFromExternal sets suppressUrlSync to true`() {
        val nav = NavigationController()
        assertFalse(nav.suppressUrlSync)
        nav.navigateFromExternal(Screen.ReleaseList)
        assertTrue(nav.suppressUrlSync)
    }

    // --- navigate ---

    @Test
    fun `navigate with duplicate consecutive screen is ignored`() {
        val nav = NavigationController()
        nav.navigate(Screen.ReleaseList)
        nav.navigate(Screen.ReleaseList)
        assertEquals(listOf(Screen.ProjectList, Screen.ReleaseList), nav.backStack)
    }

    @Test
    fun `navigate trims stack when maxStackSize exceeded`() {
        val nav = NavigationController(maxStackSize = 3)
        // Stack starts as [ProjectList]
        nav.navigate(Screen.ReleaseList)       // [ProjectList, ReleaseList]
        nav.navigate(Screen.ConnectionList)    // [ProjectList, ReleaseList, ConnectionList] — at max
        nav.navigate(Screen.TeamList)          // trims oldest → [ReleaseList, ConnectionList, TeamList]
        assertEquals(3, nav.backStack.size)
        assertEquals(Screen.ReleaseList, nav.backStack[0])
        assertEquals(Screen.ConnectionList, nav.backStack[1])
        assertEquals(Screen.TeamList, nav.backStack[2])
    }

    // --- goBack ---

    @Test
    fun `goBack returns false on single-item stack`() {
        val nav = NavigationController()
        assertFalse(nav.goBack())
        assertEquals(listOf(Screen.ProjectList), nav.backStack)
    }

    @Test
    fun `goBack removes last item and returns true`() {
        val nav = NavigationController()
        nav.navigate(Screen.ReleaseList)
        assertTrue(nav.goBack())
        assertEquals(listOf(Screen.ProjectList), nav.backStack)
        assertEquals(Screen.ProjectList, nav.currentScreen)
    }

    // --- resetTo ---

    @Test
    fun `resetTo clears stack and sets single screen`() {
        val nav = NavigationController()
        nav.navigate(Screen.ReleaseList)
        nav.navigate(Screen.ConnectionList)
        nav.navigate(Screen.TeamList)
        assertEquals(4, nav.backStack.size)

        nav.resetTo(Screen.ConnectionList)
        assertEquals(listOf(Screen.ConnectionList), nav.backStack)
        assertEquals(Screen.ConnectionList, nav.currentScreen)
    }

    // --- navigateFromExternal deep parent reconstruction ---

    @Test
    fun `navigateFromExternal with TeamManage reconstructs full parent chain`() {
        val nav = NavigationController()
        val teamManage = Screen.TeamManage(TeamId("t1"))
        nav.navigateFromExternal(teamManage)
        assertEquals(
            listOf(Screen.ProjectList, Screen.TeamList, Screen.TeamDetail(TeamId("t1")), teamManage),
            nav.backStack,
        )
    }

    @Test
    fun `navigateFromExternal with AuditLog reconstructs full parent chain`() {
        val nav = NavigationController()
        val auditLog = Screen.AuditLog(TeamId("t1"))
        nav.navigateFromExternal(auditLog)
        assertEquals(
            listOf(Screen.ProjectList, Screen.TeamList, Screen.TeamDetail(TeamId("t1")), auditLog),
            nav.backStack,
        )
    }

    @Test
    fun `navigateFromExternal with ReleaseView reconstructs ReleaseList parent`() {
        val nav = NavigationController()
        val releaseView = Screen.ReleaseView(ReleaseId("r1"))
        nav.navigateFromExternal(releaseView)
        assertEquals(
            listOf(Screen.ProjectList, Screen.ReleaseList, releaseView),
            nav.backStack,
        )
    }

    @Test
    fun `navigateFromExternal with ConnectionForm reconstructs ConnectionList parent`() {
        val nav = NavigationController()
        val form = Screen.ConnectionForm(ConnectionId("c1"))
        nav.navigateFromExternal(form)
        assertEquals(
            listOf(Screen.ProjectList, Screen.ConnectionList, form),
            nav.backStack,
        )
    }

    @Test
    fun `navigateFromExternal with ProjectAutomation reconstructs ProjectEditor parent`() {
        val nav = NavigationController()
        val auto = Screen.ProjectAutomation(ProjectId("p1"))
        nav.navigateFromExternal(auto)
        assertEquals(
            listOf(Screen.ProjectList, Screen.ProjectEditor(ProjectId("p1")), auto),
            nav.backStack,
        )
    }

    @Test
    fun `navigateFromExternal with MyInvites reconstructs TeamList parent`() {
        val nav = NavigationController()
        nav.navigateFromExternal(Screen.MyInvites)
        assertEquals(
            listOf(Screen.ProjectList, Screen.TeamList, Screen.MyInvites),
            nav.backStack,
        )
    }

    @Test
    fun `navigateFromExternal with ConnectionList sets correct stack`() {
        val nav = NavigationController()
        nav.navigateFromExternal(Screen.ConnectionList)
        assertEquals(
            listOf(Screen.ProjectList, Screen.ConnectionList),
            nav.backStack,
        )
        assertTrue(nav.suppressUrlSync)
    }

    @Test
    fun `navigateFromExternal clears previous deep stack`() {
        val nav = NavigationController()
        // Build a deep stack
        nav.navigate(Screen.ReleaseList)
        nav.navigate(Screen.ConnectionList)
        nav.navigate(Screen.TeamList)
        nav.navigate(Screen.TeamDetail(TeamId("t1")))
        assertEquals(5, nav.backStack.size)

        // navigateFromExternal should completely replace the stack
        nav.navigateFromExternal(Screen.ReleaseList)
        assertEquals(listOf(Screen.ProjectList, Screen.ReleaseList), nav.backStack)
    }

    // --- QA-CROSS-12: URL-sync for navigation ---

    @Test
    fun `suppressUrlSync starts false`() {
        val nav = NavigationController()
        assertFalse(nav.suppressUrlSync)
    }

    @Test
    fun `suppressUrlSync is set to true after navigateFromExternal`() {
        val nav = NavigationController()
        nav.navigateFromExternal(Screen.ConnectionList)
        assertTrue(nav.suppressUrlSync)
    }

    @Test
    fun `suppressUrlSync can be cleared after observation`() {
        val nav = NavigationController()
        nav.navigateFromExternal(Screen.ConnectionList)
        assertTrue(nav.suppressUrlSync)

        // Simulate what App.kt snapshotFlow collector does
        nav.suppressUrlSync = false
        assertFalse(nav.suppressUrlSync)
    }

    @Test
    fun `regular navigate does not set suppressUrlSync`() {
        val nav = NavigationController()
        nav.navigate(Screen.ReleaseList)
        assertFalse(nav.suppressUrlSync)
    }

    @Test
    fun `regular navigateToSection does not set suppressUrlSync`() {
        val nav = NavigationController()
        nav.navigateToSection(NavSection.TEAMS)
        assertFalse(nav.suppressUrlSync)
    }

    @Test
    fun `resetTo does not set suppressUrlSync`() {
        val nav = NavigationController()
        nav.navigate(Screen.ReleaseList)
        nav.resetTo(Screen.ProjectList)
        assertFalse(nav.suppressUrlSync)
    }

    @Test
    fun `goBack does not set suppressUrlSync`() {
        val nav = NavigationController()
        nav.navigate(Screen.ReleaseList)
        nav.goBack()
        assertFalse(nav.suppressUrlSync)
    }

    // --- Edge cases ---

    @Test
    fun `multiple goBack calls stop at root`() {
        val nav = NavigationController()
        nav.navigate(Screen.ReleaseList)
        nav.navigate(Screen.ConnectionList)
        assertTrue(nav.goBack())
        assertTrue(nav.goBack())
        assertFalse(nav.goBack()) // Already at root
        assertEquals(listOf(Screen.ProjectList), nav.backStack)
    }

    @Test
    fun `navigate to different screens builds correct stack`() {
        val nav = NavigationController()
        nav.navigate(Screen.ReleaseList)
        nav.navigate(Screen.ConnectionList)
        nav.navigate(Screen.TeamList)
        assertEquals(
            listOf(Screen.ProjectList, Screen.ReleaseList, Screen.ConnectionList, Screen.TeamList),
            nav.backStack,
        )
        assertEquals(Screen.TeamList, nav.currentScreen)
    }

    @Test
    fun `navigateToSection after navigateFromExternal resets suppressUrlSync state`() {
        val nav = NavigationController()
        nav.navigateFromExternal(Screen.ReleaseList)
        assertTrue(nav.suppressUrlSync)

        // Clear suppressUrlSync as the App.kt collector would
        nav.suppressUrlSync = false

        // Now navigateToSection should NOT set suppressUrlSync
        nav.navigateToSection(NavSection.CONNECTIONS)
        assertFalse(nav.suppressUrlSync)
    }

    @Test
    fun `currentScreen reflects last item in back stack`() {
        val nav = NavigationController()
        assertEquals(Screen.ProjectList, nav.currentScreen)
        nav.navigate(Screen.TeamList)
        assertEquals(Screen.TeamList, nav.currentScreen)
        nav.navigate(Screen.TeamDetail(TeamId("t1")))
        assertEquals(Screen.TeamDetail(TeamId("t1")), nav.currentScreen)
        nav.goBack()
        assertEquals(Screen.TeamList, nav.currentScreen)
    }
}
