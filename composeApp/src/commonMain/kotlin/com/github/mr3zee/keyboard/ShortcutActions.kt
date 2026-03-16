package com.github.mr3zee.keyboard

import androidx.compose.runtime.compositionLocalOf

/**
 * Screen-provided callbacks for global keyboard shortcuts.
 * Each screen provides its own actions via CompositionLocalProvider.
 */
data class ShortcutActions(
    val onSearch: (() -> Unit)? = null,
    val onCreate: (() -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
    val onSave: (() -> Unit)? = null,
    /** When true, navigation shortcuts (Alt+1-4, Alt+Left) are suppressed. */
    val hasDialogOpen: Boolean = false,
)

val LocalShortcutActions = compositionLocalOf { ShortcutActions() }
