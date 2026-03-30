package com.github.mr3zee.mockteamcity.persistence

import com.github.mr3zee.mockteamcity.model.MockState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private val persistJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class StatePersistence(
    private val stateFlow: StateFlow<MockState>,
    private val dir: File = File(System.getProperty("user.home"), ".mockteamcity"),
) {
    private val file = File(dir, "state.json")

    fun load(): MockState? {
        if (!file.exists()) return null
        return try {
            persistJson.decodeFromString<MockState>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun startAutoSave(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            stateFlow.collectLatest { state ->
                delay(2000.milliseconds) // debounce 2s
                save(state)
            }
        }
    }

    fun saveNow() {
        save(stateFlow.value)
    }

    private fun save(state: MockState) {
        try {
            dir.mkdirs()
            file.writeText(persistJson.encodeToString(MockState.serializer(), state))
        } catch (e: Exception) {
            System.err.println("Failed to save state: ${e.message}")
        }
    }
}
