package com.github.mr3zee.navigation

/**
 * Platform-specific URL routing.
 * On browser targets (WasmJS/JS), integrates with the History API.
 * On JVM Desktop, this is a no-op.
 */
interface PlatformRouter {
    /** Push a new URL path to the browser address bar without a full page reload. */
    fun pushPath(path: String)

    /** Replace the current URL path (for section switches and redirects). */
    fun replacePath(path: String)

    /** Read the current URL path. Returns "/" on non-browser platforms. */
    fun currentPath(): String

    /**
     * Register a listener for browser back/forward (popstate) events.
     * Returns a dispose function to unregister the listener.
     */
    fun onPopState(listener: (path: String) -> Unit): () -> Unit
}

expect fun createPlatformRouter(): PlatformRouter
