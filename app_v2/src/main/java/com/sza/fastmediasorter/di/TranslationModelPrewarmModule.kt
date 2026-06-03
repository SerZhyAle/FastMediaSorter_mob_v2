package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmEnabled
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModelPrewarmModule {

    @Multibinds
    @TranslationModelPrewarmEnabled
    abstract fun translationModelPrewarmMarkers(): Set<@JvmSuppressWildcards String>
}
