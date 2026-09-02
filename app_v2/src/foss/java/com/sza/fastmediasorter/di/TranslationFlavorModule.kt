package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmer
import com.sza.fastmediasorter.ui.player.helpers.NoOpTextTranslationFacadeFactory
import com.sza.fastmediasorter.ui.player.helpers.NoOpTranslationModelPrewarmer
import com.sza.fastmediasorter.ui.player.helpers.TextTranslationFacadeFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S0403: translation is ML Kit only in this app, and ML Kit is proprietary, so foss binds the no-op
 * pair. Unlike lite this is a policy exclusion rather than a size one - a free translation backend
 * would slot into these same bindings without touching src/main.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationFlavorModule {

    @Binds
    @Singleton
    abstract fun bindTextTranslationFacadeFactory(
        impl: NoOpTextTranslationFacadeFactory
    ): TextTranslationFacadeFactory

    @Binds
    @Singleton
    abstract fun bindTranslationModelPrewarmer(
        impl: NoOpTranslationModelPrewarmer
    ): TranslationModelPrewarmer
}
