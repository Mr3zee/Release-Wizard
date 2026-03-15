package com.github.mr3zee.theme

import com.github.mr3zee.i18n.LanguagePack
import java.util.prefs.Preferences

private val prefs = Preferences.userNodeForPackage(ThemePreference::class.java)
private const val KEY = "theme_preference"
private const val LANG_KEY = "language_pack"

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

actual fun loadLanguagePack(): LanguagePack {
    val name = prefs.get(LANG_KEY, LanguagePack.ENGLISH.name)
    return try {
        LanguagePack.valueOf(name)
    } catch (_: Exception) {
        LanguagePack.ENGLISH
    }
}

actual fun saveLanguagePack(pack: LanguagePack) {
    prefs.put(LANG_KEY, pack.name)
}
