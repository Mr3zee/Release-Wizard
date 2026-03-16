package com.github.mr3zee.util

enum class HostOS { MACOS, WINDOWS, LINUX, OTHER }

expect fun currentHostOS(): HostOS

/** Desktop JVM vs Browser (JS/WasmJS). */
enum class RuntimeContext { DESKTOP, BROWSER }

expect fun currentRuntimeContext(): RuntimeContext
