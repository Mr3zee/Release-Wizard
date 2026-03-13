package com.github.mr3zee

import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.auth.authModule
import com.github.mr3zee.connections.connectionsModule
import com.github.mr3zee.projects.projectsModule
import com.github.mr3zee.releases.releasesModule
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun testDbConfig() = DatabaseConfig(
    url = "jdbc:h2:mem:test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    user = "sa",
    password = "",
    driver = "org.h2.Driver",
)

fun testAuthConfig() = AuthConfig(
    username = "admin",
    password = "admin",
    sessionSignKey = "6162636465666768696a6b6c6d6e6f707172737475767778797a313233343536",
)

fun testEncryptionConfig() = EncryptionConfig(
    key = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
)

fun Application.testModule() {
    val authConfig = testAuthConfig()
    install(Koin) {
        slf4jLogger()
        modules(
            appModule(testDbConfig(), testEncryptionConfig(), authConfig),
            authModule,
            projectsModule,
            connectionsModule,
            releasesModule,
        )
    }
    install(Sessions) {
        cookie<UserSession>("SESSION") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 86400
            cookie.httpOnly = true
            transform(SessionTransportTransformerMessageAuthentication(hex(authConfig.sessionSignKey)))
        }
    }
    install(Authentication) {
        session<UserSession>("session-auth") {
            validate { it }
            challenge { call.respond(HttpStatusCode.Unauthorized, "Not authenticated") }
        }
    }
    install(ContentNegotiation) {
        json(AppJson)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respondText(cause.message ?: "Bad request", status = HttpStatusCode.BadRequest)
        }
    }
    configureRouting()
}

fun ApplicationTestBuilder.jsonClient() = createClient {
    install(ClientContentNegotiation) {
        json(AppJson)
    }
    install(HttpCookies)
}
