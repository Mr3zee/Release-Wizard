package com.github.mr3zee.theme

import com.github.mr3zee.i18n.LanguagePack

expect fun loadThemePreference(): ThemePreference
expect fun saveThemePreference(preference: ThemePreference)

expect fun loadLanguagePack(): LanguagePack
expect fun saveLanguagePack(pack: LanguagePack)
