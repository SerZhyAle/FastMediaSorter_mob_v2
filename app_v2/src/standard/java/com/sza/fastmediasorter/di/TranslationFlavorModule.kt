package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.delivery.DynamicTextTranslationFacadeFactory
import com.sza.fastmediasorter.delivery.DynamicTranslationModelPrewarmer
import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmer
import com.sza.fastmediasorter.ui.player.helpers.TextTranslationFacadeFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationFlavorModule {

    @Binds
    @Singleton
    abstract fun bindTextTranslationFacadeFactory(
        impl: DynamicTextTranslationFacadeFactory
    ): TextTranslationFacadeFactory

    @Binds
    @Singleton
    abstract fun bindTranslationModelPrewarmer(
        impl: DynamicTranslationModelPrewarmer
    ): TranslationModelPrewarmer
}
