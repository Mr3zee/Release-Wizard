package com.github.mr3zee.api

private val httpBaseUrl: String by lazy {
    System.getProperty("release.wizard.server.url", "http://localhost:8080")
}

private val wsBaseUrl: String by lazy {
    System.getProperty("release.wizard.server.ws.url", "ws://localhost:8080")
}

actual fun platformHttpBaseUrl(): String = httpBaseUrl
actual fun platformWsBaseUrl(): String = wsBaseUrl
