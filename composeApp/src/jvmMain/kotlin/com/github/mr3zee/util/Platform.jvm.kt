package com.github.mr3zee.util

actual fun currentHostOS(): HostOS {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        "mac" in osName || "darwin" in osName -> HostOS.MACOS
        "win" in osName -> HostOS.WINDOWS
        "nux" in osName || "nix" in osName -> HostOS.LINUX
        else -> HostOS.OTHER
    }
}

actual fun currentRuntimeContext(): RuntimeContext = RuntimeContext.DESKTOP
