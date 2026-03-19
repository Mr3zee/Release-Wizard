package com.github.mr3zee

import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.ProjectId
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
}
