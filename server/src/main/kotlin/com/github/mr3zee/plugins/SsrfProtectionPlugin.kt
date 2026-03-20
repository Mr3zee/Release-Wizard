package com.github.mr3zee.plugins

import com.github.mr3zee.connections.ConnectionTester.Companion.validateHostNotPrivate
import io.ktor.client.plugins.api.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.github.mr3zee.plugins.SsrfProtectionPlugin")

/**
 * CONN-C1: HttpClient plugin that validates every outgoing request URL against private/internal
 * network addresses to prevent SSRF attacks, including DNS rebinding.
 *
 * Installed on the shared HttpClient so all outgoing requests (to TeamCity, GitHub,
 * Slack, Maven repos, etc.) are checked at request time — not just at connection-test time.
 */
val SsrfProtection = createClientPlugin("SsrfProtection") {
    onRequest { request, _ ->
        val host = request.url.host
        if (host.isEmpty()) {
            throw IllegalArgumentException("SSRF protection: request URL has no host")
        }
        try {
            validateHostNotPrivate(host)
        } catch (e: IllegalArgumentException) {
            log.warn("SSRF protection blocked request to {}: {}", host, e.message)
            throw e
        }
    }
}
