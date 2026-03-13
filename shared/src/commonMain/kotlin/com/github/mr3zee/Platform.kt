package com.github.mr3zee

interface Platform {
    val name: String
}

// todo claude: unused
expect fun getPlatform(): Platform
