package com.github.mr3zee.usernotifications

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.registerAndApproveUser
import com.github.mr3zee.testModule
import com.github.mr3zee.model.UserNotification
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserNotificationRoutesTest {

    /**
     * Seeds notifications by inviting user2 to teams (each invite creates a TEAM_INVITE_RECEIVED).
     * Returns the user2 client that has the notifications.
     */
    private suspend fun ApplicationTestBuilder.seedNotifications(
        count: Int = 3,
    ): io.ktor.client.HttpClient {
        val admin = jsonClient()
        val teamId = admin.loginAndCreateTeam()
        val user2 = jsonClient()
        user2.registerAndApproveUser(admin, "user2", "user2pass")

        // Create multiple teams and invite user2 to each → generates notifications
        repeat(count) { i ->
            val extraTeamResponse = admin.post(ApiRoutes.Teams.BASE) {
                contentType(ContentType.Application.Json)
                setBody(CreateTeamRequest(name = "Team-$i"))
            }
            val extraTeamId = extraTeamResponse.body<TeamResponse>().team.id
            admin.post(ApiRoutes.Teams.invites(extraTeamId.value)) {
                contentType(ContentType.Application.Json)
                setBody(CreateInviteRequest(username = "user2"))
            }
        }
        return user2
    }

    @Test
    fun `list notifications returns seeded items`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(3)

        val response = user2.get(ApiRoutes.UserNotifications.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<UserNotificationListResponse>()
        assertEquals(3, body.notifications.size)
        assertTrue(body.notifications.all { !it.read })
    }

    @Test
    fun `unread count matches seeded notifications`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(2)

        val response = user2.get(ApiRoutes.UserNotifications.UNREAD_COUNT)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<UnreadCountResponse>()
        assertEquals(2, body.count)
    }

    @Test
    fun `mark single notification as read`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(2)

        val list = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        val notifId = list.notifications.first().id

        val markResponse = user2.post(ApiRoutes.UserNotifications.markRead(notifId))
        assertEquals(HttpStatusCode.OK, markResponse.status)

        // Verify only one is read
        val unread = user2.get(ApiRoutes.UserNotifications.UNREAD_COUNT).body<UnreadCountResponse>()
        assertEquals(1, unread.count)
    }

    @Test
    fun `mark all as read sets all to read`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(3)

        val markResponse = user2.post(ApiRoutes.UserNotifications.MARK_ALL_READ)
        assertEquals(HttpStatusCode.OK, markResponse.status)

        val unread = user2.get(ApiRoutes.UserNotifications.UNREAD_COUNT).body<UnreadCountResponse>()
        assertEquals(0, unread.count)

        val list = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        assertTrue(list.notifications.all { it.read })
    }

    @Test
    fun `delete single notification removes it`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(3)

        val list = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        val notifId = list.notifications.first().id

        val deleteResponse = user2.delete(ApiRoutes.UserNotifications.byId(notifId))
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        val afterList = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        assertEquals(2, afterList.notifications.size)
        assertTrue(afterList.notifications.none { it.id == notifId })
    }

    @Test
    fun `delete nonexistent notification returns 404`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(1)

        val response = user2.delete(ApiRoutes.UserNotifications.byId("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `delete all read removes only read notifications`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(3)

        // Mark first two as read
        val list = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        user2.post(ApiRoutes.UserNotifications.markRead(list.notifications[0].id))
        user2.post(ApiRoutes.UserNotifications.markRead(list.notifications[1].id))

        // Delete all read
        val deleteResponse = user2.delete(ApiRoutes.UserNotifications.DELETE_ALL_READ)
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Only 1 unread notification should remain
        val afterList = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        assertEquals(1, afterList.notifications.size)
        assertTrue(!afterList.notifications.first().read)
    }

    @Test
    fun `cannot delete another users notification`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(1)

        // Login as a different user (admin)
        val admin = jsonClient()
        admin.login()

        val list = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        val notifId = list.notifications.first().id

        // Admin tries to delete user2's notification
        val response = admin.delete(ApiRoutes.UserNotifications.byId(notifId))
        assertEquals(HttpStatusCode.NotFound, response.status)

        // user2's notification should still be there
        val afterList = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        assertEquals(1, afterList.notifications.size)
    }

    @Test
    fun `delete with invalid UUID returns 400`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(1)

        val response = user2.delete(ApiRoutes.UserNotifications.byId("not-a-uuid"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `mark read with invalid UUID returns 400`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(1)

        val response = user2.post(ApiRoutes.UserNotifications.markRead("not-a-uuid"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `mark read nonexistent notification returns 404`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(1)

        val response = user2.post(ApiRoutes.UserNotifications.markRead("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `unauthenticated request returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.UserNotifications.BASE)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `unread count decreases when unread notification is deleted`() = testApplication {
        application { testModule() }
        val user2 = seedNotifications(3)

        val list = user2.get(ApiRoutes.UserNotifications.BASE).body<UserNotificationListResponse>()
        // Delete an unread notification
        user2.delete(ApiRoutes.UserNotifications.byId(list.notifications.first().id))

        val unread = user2.get(ApiRoutes.UserNotifications.UNREAD_COUNT).body<UnreadCountResponse>()
        assertEquals(2, unread.count)
    }
}
