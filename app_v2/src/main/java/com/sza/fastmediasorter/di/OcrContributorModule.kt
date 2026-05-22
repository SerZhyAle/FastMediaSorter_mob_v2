package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.ocr.OcrEngineContributor
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrContributorModule {

    @Multibinds
    abstract fun bindOcrEngineContributors(): Set<@JvmSuppressWildcards OcrEngineContributor>
}
