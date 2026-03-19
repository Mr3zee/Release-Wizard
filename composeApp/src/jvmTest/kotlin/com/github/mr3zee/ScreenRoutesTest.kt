package com.github.mr3zee

import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.navigation.parseUrlPath
import com.github.mr3zee.navigation.toUrlPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenRoutesTest {

    // --- Round-trip tests: parseUrlPath(screen.toUrlPath()) == screen ---

    @Test
    fun `round-trip ProjectList`() {
        val screen = Screen.ProjectList
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip ProjectEditor with null id for new project`() {
        val screen = Screen.ProjectEditor(null)
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip ProjectEditor with existing project id`() {
        val screen = Screen.ProjectEditor(ProjectId("p1"))
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip ProjectAutomation`() {
        val screen = Screen.ProjectAutomation(ProjectId("p1"))
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip ReleaseList`() {
        val screen = Screen.ReleaseList
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip ReleaseView`() {
        val screen = Screen.ReleaseView(ReleaseId("r1"))
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip ConnectionList`() {
        val screen = Screen.ConnectionList
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip ConnectionForm with null id for new connection`() {
        val screen = Screen.ConnectionForm(null)
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip ConnectionForm with existing connection id`() {
        val screen = Screen.ConnectionForm(ConnectionId("c1"))
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip TeamList`() {
        val screen = Screen.TeamList
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip TeamDetail`() {
        val screen = Screen.TeamDetail(TeamId("t1"))
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip TeamManage`() {
        val screen = Screen.TeamManage(TeamId("t1"))
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip MyInvites`() {
        val screen = Screen.MyInvites
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `round-trip AuditLog`() {
        val screen = Screen.AuditLog(TeamId("t1"))
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    // --- Edge cases ---

    @Test
    fun `trailing slash parses same as without trailing slash`() {
        assertEquals(Screen.ReleaseList, parseUrlPath("/releases/"))
        assertEquals(Screen.ReleaseList, parseUrlPath("/releases"))
    }

    @Test
    fun `root path parses to ProjectList`() {
        assertEquals(Screen.ProjectList, parseUrlPath("/"))
    }

    @Test
    fun `empty string parses to ProjectList`() {
        assertEquals(Screen.ProjectList, parseUrlPath(""))
    }

    @Test
    fun `unknown path returns null`() {
        assertNull(parseUrlPath("/unknown"))
    }

    @Test
    fun `malformed path with double slash is handled gracefully`() {
        // "/projects//edit" splits into ["projects", "", "edit"] which matches the
        // ProjectEditor pattern with an empty-string id — the parser does not crash
        val result = parseUrlPath("/projects//edit")
        // Accepted as a ProjectEditor with empty-string ProjectId (graceful, not null)
        assertEquals(Screen.ProjectEditor(ProjectId("")), result)
    }
}
