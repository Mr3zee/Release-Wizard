package com.github.mr3zee.util

import kotlinx.browser.window

actual fun copyToClipboard(text: String) {
    try {
        window.navigator.clipboard.writeText(text)
    } catch (e: Exception) {
        println("Failed to copy to clipboard: ${e.message}")
        // Clipboard API may be unavailable in non-secure contexts
    }
}
