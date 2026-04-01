package com.github.mr3zee.testpanel.server

import com.github.mr3zee.testpanel.model.TestPanelState
import com.github.mr3zee.testpanel.model.RequestLogEntry
import com.github.mr3zee.testpanel.server.routes.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.time.LocalTime

class TestPanelServer(private val state: TestPanelState) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start(port: Int) {
        if (server != null) return
        val engine = embeddedServer(CIO, port = port) {
            configureSerialization()
            configureCors()
            configureStatusPages()
            configureResponseDelay(state)
            configureRequestLogging(state)
            configureAuth(state)
            configureRoutes(state)
        }
        try {
            engine.start(wait = false)
            server = engine
            _isRunning.value = true
        } catch (e: Exception) {
            engine.stop(gracePeriodMillis = 0, timeoutMillis = 500)
            throw e
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1000)
        server = null
        _isRunning.value = false
    }
}

private fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
}

private fun Application.configureCors() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
}

private fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = cause.message ?: "Internal server error",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }
}

private fun Application.configureResponseDelay(state: TestPanelState) {
    intercept(ApplicationCallPipeline.Plugins) {
        val delayMs = state.currentState().serverConfig.responseDelayMs
        if (delayMs > 0) {
            delay(delayMs)
        }
    }
}

private fun Application.configureRequestLogging(state: TestPanelState) {
    intercept(ApplicationCallPipeline.Monitoring) {
        proceed()
        val now = LocalTime.now()
        val timestamp = "%02d:%02d:%02d".format(now.hour, now.minute, now.second)
        state.logRequest(
            RequestLogEntry(
                method = call.request.local.method.value,
                path = call.request.local.uri,
                timestamp = timestamp,
                statusCode = call.response.status()?.value ?: 0,
            )
        )
    }
}

private fun Application.configureRoutes(state: TestPanelState) {
    routing {
        // TeamCity
        serverRoutes()
        projectRoutes(state)
        buildTypeRoutes(state)
        buildQueueRoutes(state)
        buildRoutes(state)
        subBuildRoutes(state)
        artifactRoutes(state)
        // Slack Bot API
        slackApiRoutes(state)
        // GitHub
        gitHubRepoRoutes(state)
        gitHubActionRoutes(state)
        gitHubReleaseRoutes(state)
    }
}
