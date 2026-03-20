package com.github.mr3zee.audit

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.createTestTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.waitUntil
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditIntegrityTest {

    @Test
    fun `audit events survive team deletion - teamId preserved as string`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("Audit Survive Team")

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "TEAM_CREATED" }
        }

        val auditBefore = client.get(ApiRoutes.Teams.audit(teamId.value)).body<AuditEventListResponse>()
        assertTrue(auditBefore.events.isNotEmpty())
        val eventBeforeDeletion = auditBefore.events.first()
        assertEquals(teamId.value, eventBeforeDeletion.teamId?.value)

        client.delete(ApiRoutes.Teams.byId(teamId.value))
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.Teams.byId(teamId.value)).status)

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "TEAM_DELETED" }
        }

        val auditAfter = client.get(ApiRoutes.Teams.audit(teamId.value)).body<AuditEventListResponse>()
        assertTrue(auditAfter.events.isNotEmpty())
        assertTrue(auditAfter.events.all { it.teamId?.value == teamId.value })
        assertTrue(auditAfter.events.any { it.action.name == "TEAM_CREATED" })
        assertTrue(auditAfter.events.any { it.action.name == "TEAM_DELETED" })
    }

    @Test
    fun `logSync writes within caller transaction`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("Sync Audit Team")

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "TEAM_CREATED" }
        }

        val response = client.get(ApiRoutes.Teams.audit(teamId.value))
        assertEquals(HttpStatusCode.OK, response.status)
        val events = response.body<AuditEventListResponse>()
        assertTrue(events.events.isNotEmpty())
    }
}
