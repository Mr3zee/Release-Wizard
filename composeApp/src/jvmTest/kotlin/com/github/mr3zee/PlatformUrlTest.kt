package com.github.mr3zee

import com.github.mr3zee.api.platformHttpBaseUrl
import com.github.mr3zee.api.platformWsBaseUrl
import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformUrlTest {

    @Test
    fun `HTTP base URL defaults to localhost`() {
        val url = platformHttpBaseUrl()
        assertTrue(url.startsWith("http"), "HTTP URL should start with http: $url")
        assertTrue(
            url.contains("localhost") || url.contains("127.0.0.1") || System.getProperty("release.wizard.server.url") != null,
            "HTTP URL should be localhost or configured: $url",
        )
    }

    @Test
    fun `WS base URL defaults to localhost`() {
        val url = platformWsBaseUrl()
        assertTrue(url.startsWith("ws"), "WS URL should start with ws: $url")
        assertTrue(
            url.contains("localhost") || url.contains("127.0.0.1") || System.getProperty("release.wizard.server.ws.url") != null,
            "WS URL should be localhost or configured: $url",
        )
    }

    @Test
    fun `HTTP and WS URLs use same host and port`() {
        val http = platformHttpBaseUrl()
        val ws = platformWsBaseUrl()
        // Strip protocol
        val httpHost = http.substringAfter("://")
        val wsHost = ws.substringAfter("://")
        assertTrue(httpHost == wsHost, "HTTP ($httpHost) and WS ($wsHost) should target same host")
    }
}
