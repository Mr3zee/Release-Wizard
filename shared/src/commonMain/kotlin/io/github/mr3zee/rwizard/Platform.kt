package io.github.mr3zee.rwizard

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform