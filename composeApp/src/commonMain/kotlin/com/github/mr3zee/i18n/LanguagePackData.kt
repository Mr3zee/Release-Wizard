package com.github.mr3zee.i18n

data class LanguagePackData(
    val strings: Map<String, String> = emptyMap(),
    val plurals: Map<String, PluralOverride> = emptyMap(),
) {
    companion object {
        val EMPTY = LanguagePackData()
    }
}

data class PluralOverride(
    val one: String,
    val other: String,
)
