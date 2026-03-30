package com.github.mr3zee.testpanel

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.mr3zee.testpanel.model.TestPanelState
import com.github.mr3zee.testpanel.persistence.StatePersistence
import com.github.mr3zee.testpanel.server.TestPanelServer
import com.github.mr3zee.testpanel.ui.TestPanelApp
import kotlinx.coroutines.*
import java.awt.Dimension

fun main() {
    val state = TestPanelState()
    val server = TestPanelServer(state)
    val persistence = StatePersistence(state.state)

    // Load saved state
    persistence.load()?.let { saved ->
        state.loadState(saved.copy(
            builds = emptyList(),
            slackMessages = emptyList(),
            ghRuns = emptyList(),
            webhookSendHistory = emptyList(),
        ))
    }

    // Start auto-save
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    persistence.startAutoSave(scope)

    application {
        val windowState = rememberWindowState(
            size = DpSize(1400.dp, 900.dp),
            position = WindowPosition.PlatformDefault,
        )

        Window(
            onCloseRequest = {
                server.stop()
                persistence.saveNow()
                scope.cancel()
                exitApplication()
            },
            title = "Release Wizard \u2014 Test Panel",
            state = windowState,
        ) {
            window.minimumSize = Dimension(1000, 700)
            TestPanelApp(state, server)
        }
    }
}
