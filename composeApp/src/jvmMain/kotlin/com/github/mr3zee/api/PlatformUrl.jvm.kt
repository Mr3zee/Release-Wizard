package com.github.mr3zee.api

import com.github.mr3zee.SERVER_PORT

actual fun platformHttpBaseUrl(): String = "http://localhost:$SERVER_PORT"
actual fun platformWsBaseUrl(): String = "ws://localhost:$SERVER_PORT"
