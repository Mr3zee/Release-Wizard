package com.github.mr3zee

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ReleaseWizard",
    ) {
        App()
    }
}