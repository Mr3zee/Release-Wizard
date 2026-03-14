package com.github.mr3zee.tags

import org.koin.dsl.module

val tagsModule = module {
    single<TagRepository> { ExposedTagRepository(get()) }
    single<TagService> { DefaultTagService(get()) }
}
