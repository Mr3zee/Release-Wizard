@file:OptIn(ExperimentalUuidApi::class)

package io.github.mr3zee.rwizard.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed class Screen {
    data object ProjectList : Screen()
    data class ProjectDetail(val projectId: Uuid) : Screen()
    data class ProjectEditor(val projectId: Uuid?) : Screen() // null for new project
    data class ReleaseCreation(val projectId: Uuid) : Screen()
    data class ReleaseMonitor(val releaseId: Uuid) : Screen()
    data object ConnectionList : Screen()
    data class ConnectionEditor(val connectionId: Uuid?) : Screen() // null for new connection
}

class NavigationManager {
    private val _currentScreen = MutableStateFlow<Screen>(Screen.ProjectList)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()
    
    private val screenStack = mutableListOf<Screen>(Screen.ProjectList)
    
    fun navigateTo(screen: Screen) {
        screenStack.add(screen)
        _currentScreen.value = screen
    }
    
    fun navigateBack(): Boolean {
        return if (screenStack.size > 1) {
            screenStack.removeLastOrNull()
            _currentScreen.value = screenStack.last()
            true
        } else {
            false
        }
    }
    
    fun navigateToRoot() {
        screenStack.clear()
        screenStack.add(Screen.ProjectList)
        _currentScreen.value = Screen.ProjectList
    }
    
    fun canNavigateBack(): Boolean = screenStack.size > 1
}
