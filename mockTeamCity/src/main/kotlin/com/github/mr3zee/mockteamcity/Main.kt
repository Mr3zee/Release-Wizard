package com.github.mr3zee.mockteamcity

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.mr3zee.mockteamcity.model.MockTeamCityState
import com.github.mr3zee.mockteamcity.persistence.StatePersistence
import com.github.mr3zee.mockteamcity.server.MockTeamCityServer
import com.github.mr3zee.mockteamcity.ui.MockTeamCityApp
import kotlinx.coroutines.*
import java.awt.Dimension

fun main() {
    val state = MockTeamCityState()
    val server = MockTeamCityServer(state)
    val persistence = StatePersistence(state.state)

    // Load saved state
    persistence.load()?.let { saved ->
        // Restore config and projects/build types, but not builds (start fresh)
        state.loadState(saved.copy(builds = emptyList()))
    }

    // Start auto-save
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    persistence.startAutoSave(scope)

    application {
        val windowState = rememberWindowState(
            size = DpSize(1200.dp, 800.dp),
            position = WindowPosition.PlatformDefault,
        )

        Window(
            onCloseRequest = {
                server.stop()
                persistence.saveNow()
                scope.cancel()
                exitApplication()
            },
            title = "Mock TeamCity",
            state = windowState,
        ) {
            window.minimumSize = Dimension(900, 600)
            MockTeamCityApp(state, server)
        }
    }
}
