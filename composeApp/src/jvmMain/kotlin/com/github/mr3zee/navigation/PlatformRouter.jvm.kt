package com.github.mr3zee.navigation

/** No-op router for Desktop JVM — no browser URL to manage. */
actual fun createPlatformRouter(): PlatformRouter = object : PlatformRouter {
    override fun pushPath(path: String) {}
    override fun replacePath(path: String) {}
    override fun currentPath(): String = "/"
    override fun currentQuery(): String = ""
    override fun onPopState(listener: (path: String) -> Unit): () -> Unit = {}
}
