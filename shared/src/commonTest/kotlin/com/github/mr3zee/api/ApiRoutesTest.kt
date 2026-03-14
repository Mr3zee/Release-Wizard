package com.github.mr3zee.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRoutesTest {

    @Test
    fun `blockExecution builds correct path`() {
        assertEquals(
            // todo claude: ApiRoutes not used
            "/api/v1/releases/r1/blocks/b1",
            ApiRoutes.Releases.blockExecution("r1", "b1"),
        )
    }

    @Test
    fun `restartBlock builds correct path`() {
        assertEquals(
            // todo claude: ApiRoutes not used
            "/api/v1/releases/r1/blocks/b1/restart",
            ApiRoutes.Releases.restartBlock("r1", "b1"),
        )
    }

    @Test
    fun `approveBlock builds correct path`() {
        assertEquals(
            // todo claude: ApiRoutes not used
            "/api/v1/releases/r1/blocks/b1/approve",
            ApiRoutes.Releases.approveBlock("r1", "b1"),
        )
    }

    @Test
    fun `restartBlock is prefixed by blockExecution`() {
        val base = ApiRoutes.Releases.blockExecution("r1", "b1")
        val restart = ApiRoutes.Releases.restartBlock("r1", "b1")
        assertTrue(
            restart.startsWith(base),
            "restartBlock should start with blockExecution path",
        )
    }

    @Test
    fun `approveBlock is prefixed by blockExecution`() {
        val base = ApiRoutes.Releases.blockExecution("r1", "b1")
        val approve = ApiRoutes.Releases.approveBlock("r1", "b1")
        assertTrue(
            approve.startsWith(base),
            "approveBlock should start with blockExecution path",
        )
    }
}
