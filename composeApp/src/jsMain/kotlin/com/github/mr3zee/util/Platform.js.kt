package com.github.mr3zee.util

import kotlinx.browser.window

actual fun currentHostOS(): HostOS {
    val platform = window.navigator.platform.lowercase()
    return when {
        "mac" in platform -> HostOS.MACOS
        "win" in platform -> HostOS.WINDOWS
        "linux" in platform -> HostOS.LINUX
        else -> HostOS.OTHER
    }
}

actual fun currentRuntimeContext(): RuntimeContext = RuntimeContext.BROWSER
