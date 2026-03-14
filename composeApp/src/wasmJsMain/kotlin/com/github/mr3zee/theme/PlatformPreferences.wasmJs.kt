package com.github.mr3zee.theme

import kotlinx.browser.window

private const val KEY = "theme_preference"

actual fun loadThemePreference(): ThemePreference {
    val name = window.localStorage.getItem(KEY) ?: ThemePreference.SYSTEM.name
    return try {
        ThemePreference.valueOf(name)
    } catch (_: Exception) {
        ThemePreference.SYSTEM
    }
}

actual fun saveThemePreference(preference: ThemePreference) {
    window.localStorage.setItem(KEY, preference.name)
}
