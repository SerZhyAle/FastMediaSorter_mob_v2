package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.translation.TranslationModelPrewarmEnabled
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object TranslationModelPrewarmAvailabilityModule {

    @Provides
    @IntoSet
    @TranslationModelPrewarmEnabled
    fun translation(): String = "translation"
}
