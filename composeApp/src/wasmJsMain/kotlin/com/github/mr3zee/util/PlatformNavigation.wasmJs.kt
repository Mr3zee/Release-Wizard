package com.github.mr3zee.util

import kotlinx.browser.window

actual fun navigateToExternalUrl(url: String) {
    window.location.href = url
}
