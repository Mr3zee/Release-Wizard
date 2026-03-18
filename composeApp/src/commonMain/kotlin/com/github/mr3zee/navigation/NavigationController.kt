package com.github.mr3zee.navigation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf

/**
 * Simple back-stack based navigation controller.
 * Manages a stack of [Screen]s with a max depth of [maxStackSize].
 */
class NavigationController(
    initialScreen: Screen = Screen.ProjectList,
    private val maxStackSize: Int = 20,
) {
    private val _backStack = mutableStateListOf(initialScreen)

    val currentScreen by derivedStateOf { _backStack.last() }
    // todo claude: unused
    val canGoBack by derivedStateOf { _backStack.size > 1 }

    fun navigate(screen: Screen) {
        // Avoid pushing the same screen twice
        if (_backStack.lastOrNull() == screen) return
        _backStack.add(screen)
        // Trim from the front if stack exceeds max
        while (_backStack.size > maxStackSize) {
            _backStack.removeAt(0)
        }
    }

    fun goBack(): Boolean {
        if (_backStack.size <= 1) return false
        _backStack.removeLast()
        return true
    }

    fun resetTo(screen: Screen) {
        _backStack.clear()
        _backStack.add(screen)
    }
}
