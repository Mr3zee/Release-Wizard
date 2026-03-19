package com.github.mr3zee.keyboard

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.github.mr3zee.navigation.NavSection

/**
 * Global keyboard event handler attached via `Modifier.onKeyEvent` (bubble phase)
 * on the top-level Surface in App.kt.
 *
 * Because it uses `onKeyEvent` (not `onPreviewKeyEvent`), it fires AFTER all child
 * handlers. This means focused text fields, the DagEditor's onKeyEvent, and
 * TemplateAutocompleteField's onPreviewKeyEvent all get first shot at consuming events.
 * The global handler only processes events that no child claimed.
 */
fun handleGlobalKeyEvent(
    event: KeyEvent,
    shortcutActions: ShortcutActions,
    onNavigateToSection: (NavSection) -> Unit,
    onGoBack: () -> Boolean,
    onToggleTheme: () -> Unit,
    onToggleShortcutsOverlay: () -> Unit,
    isShortcutsOverlayOpen: Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val isModifier = event.isCtrlPressed || event.isMetaPressed
    val isAlt = event.isAltPressed
    val isShift = event.isShiftPressed

    // ── Escape to close shortcuts overlay ───────────────────────────
    if (isShortcutsOverlayOpen && event.key == Key.Escape) {
        onToggleShortcutsOverlay()
        return true
    }

    // ── Navigation shortcuts (Alt + number / Alt + Left) ────────────
    // Suppressed when a dialog is open (unsaved-changes guard)
    if (!shortcutActions.hasDialogOpen && isAlt && !isModifier) {
        when (event.key) {
            Key.One -> { onNavigateToSection(NavSection.PROJECTS); return true }
            Key.Two -> { onNavigateToSection(NavSection.RELEASES); return true }
            Key.Three -> { onNavigateToSection(NavSection.CONNECTIONS); return true }
            Key.Four -> { onNavigateToSection(NavSection.TEAMS); return true }
            Key.DirectionLeft -> { onGoBack(); return true }
        }
    }

    // ── Actions with Ctrl/Cmd modifier ──────────────────────────────
    if (isModifier) {
        when {
            // Ctrl+Shift+N → Create
            isShift && event.key == Key.N -> {
                shortcutActions.onCreate?.invoke()
                return shortcutActions.onCreate != null
            }
            // Ctrl+Shift+D → Toggle theme
            isShift && event.key == Key.D -> {
                onToggleTheme()
                return true
            }
            // Ctrl+K → Focus search
            !isShift && event.key == Key.K -> {
                shortcutActions.onSearch?.invoke()
                return shortcutActions.onSearch != null
            }
            // Ctrl+S → Save
            !isShift && event.key == Key.S -> {
                shortcutActions.onSave?.invoke()
                return shortcutActions.onSave != null
            }
            // Ctrl+/ → Toggle shortcuts overlay
            !isShift && event.key == Key.Slash -> {
                onToggleShortcutsOverlay()
                return true
            }
            // Ctrl+Enter → Form submit (same as save for forms)
            !isShift && event.key == Key.Enter -> {
                shortcutActions.onSave?.invoke()
                return shortcutActions.onSave != null
            }
        }
    }

    // ── F5 → Refresh (unmodified) ───────────────────────────────────
    if (!isModifier && !isAlt && !isShift && event.key == Key.F5) {
        shortcutActions.onRefresh?.invoke()
        return shortcutActions.onRefresh != null
    }

    return false
}
