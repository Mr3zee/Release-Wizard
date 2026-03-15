package com.github.mr3zee.theme

import com.github.mr3zee.i18n.LanguagePack
import kotlinx.browser.window

private const val KEY = "theme_preference"
private const val LANG_KEY = "language_pack"

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

actual fun loadLanguagePack(): LanguagePack {
    val name = window.localStorage.getItem(LANG_KEY) ?: LanguagePack.ENGLISH.name
    return try {
        LanguagePack.valueOf(name)
    } catch (_: Exception) {
        LanguagePack.ENGLISH
    }
}

actual fun saveLanguagePack(pack: LanguagePack) {
    window.localStorage.setItem(LANG_KEY, pack.name)
}
