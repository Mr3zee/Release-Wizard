package com.github.mr3zee.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Screen-provided callbacks for global keyboard shortcuts.
 * Each screen provides its own actions via [ProvideShortcutActions].
 */
data class ShortcutActions(
    val onSearch: (() -> Unit)? = null,
    val onCreate: (() -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
    val onSave: (() -> Unit)? = null,
    /** When true, navigation shortcuts (Alt+1-4, Alt+Left) are suppressed. */
    val hasDialogOpen: Boolean = false,
)

/**
 * Callback provided by [App] to let screens push their [ShortcutActions]
 * up to the app-level key handler. Replaces the broken CompositionLocal
 * propagation (CompositionLocals only flow downward, so a parent SideEffect
 * could never read a child-provided override).
 */
val LocalShortcutActionsSetter = staticCompositionLocalOf<(ShortcutActions) -> Unit> { {} }

/**
 * Registers [actions] with the app-level keyboard shortcut handler.
 * Resets to empty on disposal (screen leaves composition).
 */
@Composable
fun ProvideShortcutActions(actions: ShortcutActions, content: @Composable () -> Unit) {
    val setter = LocalShortcutActionsSetter.current
    SideEffect { setter(actions) }
    DisposableEffect(Unit) { onDispose { setter(ShortcutActions()) } }
    content()
}
