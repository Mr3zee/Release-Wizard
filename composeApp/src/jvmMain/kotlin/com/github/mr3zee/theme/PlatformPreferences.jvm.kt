package com.github.mr3zee.theme

import java.util.prefs.Preferences

private val prefs = Preferences.userNodeForPackage(ThemePreference::class.java)
private const val KEY = "theme_preference"

actual fun loadThemePreference(): ThemePreference {
    val name = prefs.get(KEY, ThemePreference.SYSTEM.name)
    return try {
        ThemePreference.valueOf(name)
    } catch (_: Exception) {
        ThemePreference.SYSTEM
    }
}

actual fun saveThemePreference(preference: ThemePreference) {
    prefs.put(KEY, preference.name)
}
