package com.github.mr3zee.navigation

import kotlinx.browser.window
import org.w3c.dom.events.Event

/** Browser-based router using the History API for JS target. */
actual fun createPlatformRouter(): PlatformRouter = object : PlatformRouter {
    override fun pushPath(path: String) {
        window.history.pushState(null, "", path)
    }

    override fun replacePath(path: String) {
        window.history.replaceState(null, "", path)
    }

    override fun currentPath(): String = window.location.pathname

    override fun currentQuery(): String = window.location.search.removePrefix("?")

    override fun onPopState(listener: (path: String) -> Unit): () -> Unit {
        // Store a stable reference for both addEventListener and removeEventListener
        val handler: (Event) -> Unit = { listener(window.location.pathname) }
        window.addEventListener("popstate", handler)
        return { window.removeEventListener("popstate", handler) }
    }
}
