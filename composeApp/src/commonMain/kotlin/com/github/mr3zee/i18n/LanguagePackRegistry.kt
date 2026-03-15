package com.github.mr3zee.i18n

import com.github.mr3zee.i18n.packs.*

object LanguagePackRegistry {
    fun getData(pack: LanguagePack): LanguagePackData = when (pack) {
        LanguagePack.ENGLISH -> LanguagePackData.EMPTY
        LanguagePack.GEN_Z -> GenZPack.data
        LanguagePack.RICK_AND_MORTY -> RickAndMortyPack.data
        LanguagePack.HELLDIVERS -> HelldiversPack.data
        LanguagePack.ALL_LIES -> AllLiesPack.data
        LanguagePack.EMOJIS -> EmojisPack.data
        LanguagePack.RICK_ROLL -> RickRollPack.data
    }
}
