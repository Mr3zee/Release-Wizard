package com.github.mr3zee

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform