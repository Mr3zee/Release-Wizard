package com.github.mr3zee.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    } catch (e: Exception) {
        println("Failed to copy to clipboard: ${e.message}")
        // Clipboard may be unavailable (headless, Wayland, etc.)
    }
}
