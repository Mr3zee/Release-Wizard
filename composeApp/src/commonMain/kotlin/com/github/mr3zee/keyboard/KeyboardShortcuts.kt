package com.github.mr3zee.keyboard

import androidx.compose.ui.input.key.Key
import com.github.mr3zee.util.HostOS
import com.github.mr3zee.util.currentHostOS

// ── Data model ───────────────────────────────────────────────────────────

data class KeyboardShortcut(
    val id: String,
    val key: Key,
    val modifiers: Set<ShortcutModifier> = emptySet(),
    val category: ShortcutCategory,
    val descriptionKey: String,
    val desktopOnly: Boolean = false,
)

enum class ShortcutModifier { CTRL_OR_CMD, SHIFT, ALT }

enum class ShortcutCategory {
    NAVIGATION,
    ACTIONS,
    EDITOR,
}

// ── Display helpers ──────────────────────────────────────────────────────

fun ShortcutModifier.displayString(): String {
    val isMac = currentHostOS() == HostOS.MACOS
    return when (this) {
        ShortcutModifier.CTRL_OR_CMD -> if (isMac) "\u2318" else "Ctrl"
        ShortcutModifier.SHIFT -> if (isMac) "\u21E7" else "Shift"
        ShortcutModifier.ALT -> if (isMac) "\u2325" else "Alt"
    }
}

fun Key.displayName(): String = when (this) {
    Key.One -> "1"
    Key.Two -> "2"
    Key.Three -> "3"
    Key.Four -> "4"
    Key.A -> "A"
    Key.C -> "C"
    Key.D -> "D"
    Key.K -> "K"
    Key.N -> "N"
    Key.S -> "S"
    Key.V -> "V"
    Key.Z -> "Z"
    Key.Slash -> "/"
    Key.DirectionLeft -> "\u2190"
    Key.DirectionUp -> "\u2191"
    Key.DirectionDown -> "\u2193"
    Key.Enter -> "\u21B5"
    Key.Escape -> "Esc"
    Key.Delete -> "Del"
    Key.Backspace -> "\u232B"
    Key.Home -> "Home"
    Key.MoveEnd -> "End"
    Key.F5 -> "F5"
    else -> "?"
}

/** Returns the list of display strings for this shortcut: one per modifier + the key. */
fun KeyboardShortcut.displayParts(): List<String> {
    val parts = mutableListOf<String>()
    // Stable ordering: Ctrl/Cmd → Shift → Alt
    for (mod in listOf(ShortcutModifier.CTRL_OR_CMD, ShortcutModifier.SHIFT, ShortcutModifier.ALT)) {
        if (mod in modifiers) parts.add(mod.displayString())
    }
    parts.add(key.displayName())
    return parts
}

// ── Shortcut registry (single source of truth) ──────────────────────────

val AllShortcuts: List<KeyboardShortcut> = listOf(
    // Navigation
    KeyboardShortcut("nav.projects", Key.One, setOf(ShortcutModifier.ALT), ShortcutCategory.NAVIGATION, "shortcut_nav_projects"),
    KeyboardShortcut("nav.releases", Key.Two, setOf(ShortcutModifier.ALT), ShortcutCategory.NAVIGATION, "shortcut_nav_releases"),
    KeyboardShortcut("nav.connections", Key.Three, setOf(ShortcutModifier.ALT), ShortcutCategory.NAVIGATION, "shortcut_nav_connections"),
    KeyboardShortcut("nav.teams", Key.Four, setOf(ShortcutModifier.ALT), ShortcutCategory.NAVIGATION, "shortcut_nav_teams"),
    KeyboardShortcut("nav.back", Key.DirectionLeft, setOf(ShortcutModifier.ALT), ShortcutCategory.NAVIGATION, "shortcut_nav_back"),

    // Actions
    KeyboardShortcut("action.search", Key.K, setOf(ShortcutModifier.CTRL_OR_CMD), ShortcutCategory.ACTIONS, "shortcut_action_search"),
    KeyboardShortcut("action.create", Key.N, setOf(ShortcutModifier.CTRL_OR_CMD, ShortcutModifier.SHIFT), ShortcutCategory.ACTIONS, "shortcut_action_create"),
    KeyboardShortcut("action.refresh", Key.F5, emptySet(), ShortcutCategory.ACTIONS, "shortcut_action_refresh", desktopOnly = true),
    KeyboardShortcut("action.save", Key.S, setOf(ShortcutModifier.CTRL_OR_CMD), ShortcutCategory.ACTIONS, "shortcut_action_save"),
    KeyboardShortcut("action.help", Key.Slash, setOf(ShortcutModifier.CTRL_OR_CMD), ShortcutCategory.ACTIONS, "shortcut_action_help"),
    KeyboardShortcut("action.theme", Key.D, setOf(ShortcutModifier.CTRL_OR_CMD, ShortcutModifier.SHIFT), ShortcutCategory.ACTIONS, "shortcut_action_theme"),
    // Editor (display-only — handled by DagEditorScreen's onKeyEvent)
    KeyboardShortcut("editor.undo", Key.Z, setOf(ShortcutModifier.CTRL_OR_CMD), ShortcutCategory.EDITOR, "shortcut_editor_undo"),
    KeyboardShortcut("editor.redo", Key.Z, setOf(ShortcutModifier.CTRL_OR_CMD, ShortcutModifier.SHIFT), ShortcutCategory.EDITOR, "shortcut_editor_redo"),
    KeyboardShortcut("editor.copy", Key.C, setOf(ShortcutModifier.CTRL_OR_CMD), ShortcutCategory.EDITOR, "shortcut_editor_copy"),
    KeyboardShortcut("editor.paste", Key.V, setOf(ShortcutModifier.CTRL_OR_CMD), ShortcutCategory.EDITOR, "shortcut_editor_paste"),
    KeyboardShortcut("editor.selectAll", Key.A, setOf(ShortcutModifier.CTRL_OR_CMD), ShortcutCategory.EDITOR, "shortcut_editor_select_all"),
    KeyboardShortcut("editor.delete", Key.Delete, emptySet(), ShortcutCategory.EDITOR, "shortcut_editor_delete"),
)

val ShortcutsByCategory: Map<ShortcutCategory, List<KeyboardShortcut>> =
    AllShortcuts.groupBy { it.category }
