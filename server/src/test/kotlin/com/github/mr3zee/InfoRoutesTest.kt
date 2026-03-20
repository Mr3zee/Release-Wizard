package com.github.mr3zee

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfoRoutesTest {

    @Test
    fun `root endpoint returns service info`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<Map<String, String>>()
        assertEquals("Release Wizard API", body["service"])
        // INFRA-L4: Version no longer disclosed; status field present instead
        assertEquals("running", body["status"])
    }
}
