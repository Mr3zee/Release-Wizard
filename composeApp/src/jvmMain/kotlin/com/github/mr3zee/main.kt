package com.github.mr3zee

import io.github.forketyfork.composeuittest.WindowConfig
import io.github.forketyfork.composeuittest.runApplication

fun main() =
    runApplication(
        windowConfig = WindowConfig(
            title = "Release Wizard",
            minimumWidth = 1200,
            minimumHeight = 800,
        ),
    ) {
        App()
    }
