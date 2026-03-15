package com.github.mr3zee

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.forketyfork.composeuittest.ComposeUiTestServer
import io.github.forketyfork.composeuittest.ComposeUiTestServerConfig
import java.awt.Dimension

@OptIn(ExperimentalTestApi::class)
fun main() {
    if (System.getenv("COMPOSE_UI_TEST_SERVER_ENABLED") == "true") {
        runComposeUiTest {
            setContent { App() }
            ComposeUiTestServer(this, ComposeUiTestServerConfig()).start().awaitTermination()
        }
    } else {
        application {
            // Default to 16" MacBook Pro logical resolution (1728×1117)
            val state = rememberWindowState(
                size = DpSize(1728.dp, 1117.dp),
                position = WindowPosition.PlatformDefault,
            )
            Window(
                onCloseRequest = ::exitApplication,
                title = "Release Wizard",
                state = state,
            ) {
                window.minimumSize = Dimension(1200, 800)
                App()
            }
        }
    }
}
