package com.github.mr3zee.api

import kotlinx.browser.window

actual fun platformHttpBaseUrl(): String = window.location.origin

actual fun platformWsBaseUrl(): String {
    val protocol = if (window.location.protocol == "https:") "wss:" else "ws:"
    return "$protocol//${window.location.host}"
}
