package com.github.mr3zee.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot

/**
 * Simple back-stack based navigation controller.
 * Manages a stack of [Screen]s with a max depth of [maxStackSize].
 */
@Stable
class NavigationController(
    initialScreen: Screen = Screen.ProjectList,
    private val maxStackSize: Int = 20,
) {
    private val _backStack = mutableStateListOf(initialScreen)

    val currentScreen by derivedStateOf { _backStack.lastOrNull() ?: Screen.ProjectList }

    /** Read-only snapshot of the current back stack. */
    val backStack: List<Screen> get() = _backStack.toList()

    /**
     * When true, the next currentScreen change should NOT be pushed to the browser URL.
     * Set by [navigateFromExternal] to prevent feedback loops with the browser history.
     * Cleared by the snapshotFlow collector in App.kt after observing the change.
     * Uses MutableState so the snapshot system tracks it atomically with back stack changes.
     */
    var suppressUrlSync: Boolean by mutableStateOf(false)
        internal set

    /** Push [screen] onto the back stack. Does nothing if the screen is already on top. */
    fun navigate(screen: Screen) {
        if (_backStack.lastOrNull() == screen) return
        _backStack.add(screen)
        while (_backStack.size > maxStackSize) {
            _backStack.removeAt(0)
        }
    }

    /** Pop the top screen. Returns false if at the root (single-item stack). */
    fun goBack(): Boolean {
        if (_backStack.size <= 1) return false
        _backStack.removeLast()
        return true
    }

    /** Clear the entire back stack and set [screen] as the only entry. */
    fun resetTo(screen: Screen) {
        _backStack.clear()
        _backStack.add(screen)
    }

    /**
     * Navigate to a top-level section using **replace** semantics.
     * The back stack becomes `ProjectList` or `[ProjectList, target]`.
     * This prevents back-stack bloat from frequent section switching.
     */
    fun navigateToSection(section: NavSection) {
        val target = section.toScreen()
        _backStack.clear()
        _backStack.add(Screen.ProjectList)
        if (target != Screen.ProjectList) {
            _backStack.add(target)
        }
    }

    /**
     * Navigate to a screen from an external source (browser URL / popstate).
     * Reconstructs the proper parent stack for detail screens and sets [suppressUrlSync]
     * to prevent the snapshotFlow URL observer from pushing the URL back.
     * All mutations are batched in a single snapshot transaction.
     */
    fun navigateFromExternal(screen: Screen) {
        Snapshot.withMutableSnapshot {
            suppressUrlSync = true
            _backStack.clear()
            _backStack.add(Screen.ProjectList)

            // Reconstruct the full parent chain for the target screen
            when (screen) {
                // Top-level screens: just add them after ProjectList
                Screen.ProjectList -> { /* already in stack */ }
                Screen.ReleaseList -> _backStack.add(Screen.ReleaseList)
                Screen.ConnectionList -> _backStack.add(Screen.ConnectionList)
                Screen.TeamList -> _backStack.add(Screen.TeamList)

                // Direct children of a section
                is Screen.ProjectEditor -> _backStack.add(screen)
                is Screen.ReleaseView -> {
                    _backStack.add(Screen.ReleaseList)
                    _backStack.add(screen)
                }
                is Screen.ConnectionForm -> {
                    _backStack.add(Screen.ConnectionList)
                    _backStack.add(screen)
                }
                is Screen.TeamDetail -> {
                    _backStack.add(Screen.TeamList)
                    _backStack.add(screen)
                }
                Screen.MyInvites -> {
                    _backStack.add(Screen.TeamList)
                    _backStack.add(screen)
                }

                // Deep detail screens — include intermediate parent
                is Screen.TeamManage -> {
                    _backStack.add(Screen.TeamList)
                    _backStack.add(Screen.TeamDetail(screen.teamId))
                    _backStack.add(screen)
                }
                is Screen.AuditLog -> {
                    _backStack.add(Screen.TeamList)
                    _backStack.add(Screen.TeamDetail(screen.teamId))
                    _backStack.add(screen)
                }
                is Screen.ProjectAutomation -> {
                    _backStack.add(Screen.ProjectEditor(screen.projectId))
                    _backStack.add(screen)
                }

                Screen.Profile -> {
                    _backStack.add(Screen.Profile)
                }
                Screen.AdminUsers -> {
                    _backStack.add(Screen.Profile)
                    _backStack.add(Screen.AdminUsers)
                }
                is Screen.ResetPassword -> {
                    _backStack.clear()
                    _backStack.add(screen)
                }
            }
        }
    }
}
