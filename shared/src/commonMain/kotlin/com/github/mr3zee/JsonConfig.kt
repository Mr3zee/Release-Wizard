package com.github.mr3zee

import kotlinx.serialization.json.Json

val AppJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
}
