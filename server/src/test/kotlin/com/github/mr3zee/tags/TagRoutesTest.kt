package com.github.mr3zee.tags

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagRoutesTest {

    @Test
    fun `list tags returns empty list initially`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Tags.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<TagListResponse>()
        assertTrue(body.tags.isEmpty())
    }

    @Test
    fun `rename nonexistent tag returns zero updated`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.put("${ApiRoutes.Tags.BASE}/nonexistent") {
            contentType(ContentType.Application.Json)
            setBody(RenameTagRequest(newName = "new-name"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `rename tag with blank name returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.put("${ApiRoutes.Tags.BASE}/some-tag") {
            contentType(ContentType.Application.Json)
            setBody(RenameTagRequest(newName = ""))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `delete nonexistent tag returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.delete("${ApiRoutes.Tags.BASE}/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `non-admin cannot rename tags`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        val userClient = jsonClient()

        // First user is admin
        adminClient.login("admin", "adminpass")

        // Second user is regular USER
        userClient.login("user2", "user2pass")

        val response = userClient.put("${ApiRoutes.Tags.BASE}/some-tag") {
            contentType(ContentType.Application.Json)
            setBody(RenameTagRequest(newName = "new-name"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `non-admin cannot delete tags`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        val userClient = jsonClient()

        // First user is admin
        adminClient.login("admin", "adminpass")

        // Second user is regular USER
        userClient.login("user2", "user2pass")

        val response = userClient.delete("${ApiRoutes.Tags.BASE}/some-tag")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `unauthenticated tag request returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Tags.BASE)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
