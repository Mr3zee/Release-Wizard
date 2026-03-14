package com.github.mr3zee.util

import kotlinx.browser.window

@OptIn(ExperimentalWasmJsInterop::class)
actual fun copyToClipboard(text: String) {
    try {
        window.navigator.clipboard.writeText(text)
    } catch (_: Exception) {
        // todo claude: log error
        // Clipboard API may be unavailable in non-secure contexts
    }
}
