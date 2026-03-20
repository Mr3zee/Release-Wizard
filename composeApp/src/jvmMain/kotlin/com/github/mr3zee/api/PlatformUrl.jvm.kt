package com.github.mr3zee.api

private val logger = java.util.logging.Logger.getLogger("PlatformUrl")

private val httpBaseUrl: String by lazy {
    val url = System.getenv("SERVER_URL")
        ?: System.getProperty("release.wizard.server.url", "http://localhost:8080")
    if (!url.startsWith("https://") && !isLocalhost(url)) {
        logger.warning("Server URL does not use HTTPS: $url — traffic will not be encrypted")
    }
    url
}

private val wsBaseUrl: String by lazy {
    System.getenv("SERVER_WS_URL")
        ?: System.getProperty("release.wizard.server.ws.url", deriveWsUrl(httpBaseUrl))
}

private fun deriveWsUrl(httpUrl: String): String = when {
    httpUrl.startsWith("https://") -> "wss://" + httpUrl.removePrefix("https://")
    httpUrl.startsWith("http://") -> "ws://" + httpUrl.removePrefix("http://")
    else -> "ws://$httpUrl"
}

private fun isLocalhost(url: String): Boolean {
    val host = url.removePrefix("https://").removePrefix("http://").substringBefore("/").substringBefore(":")
    return host == "localhost" || host == "127.0.0.1"
}

actual fun platformHttpBaseUrl(): String = httpBaseUrl
actual fun platformWsBaseUrl(): String = wsBaseUrl
