package com.github.mr3zee.api

private val httpBaseUrl: String by lazy {
    System.getenv("SERVER_URL")
        ?: System.getProperty("release.wizard.server.url", "http://localhost:8080")
}

private val wsBaseUrl: String by lazy {
    System.getenv("SERVER_WS_URL")
        ?: System.getProperty("release.wizard.server.ws.url", "ws://localhost:8080")
}

actual fun platformHttpBaseUrl(): String = httpBaseUrl
actual fun platformWsBaseUrl(): String = wsBaseUrl
