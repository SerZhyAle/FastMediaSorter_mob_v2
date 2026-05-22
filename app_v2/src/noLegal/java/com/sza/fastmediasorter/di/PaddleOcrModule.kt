package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngine
import com.sza.fastmediasorter.domain.ocr.OcrEngineContributor
import com.sza.fastmediasorter.domain.ocr.PaddleOcrEngine
import com.sza.fastmediasorter.domain.ocr.PaddleOcrEngineContributor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PaddleOfflineOcr

@Module
@InstallIn(SingletonComponent::class)
object PaddleOcrModule {

    @Provides
    @Singleton
    @PaddleOfflineOcr
    fun providePaddleOcrEngine(
        paddleOcrEngine: PaddleOcrEngine
    ): OfflineOcrEngine {
        return paddleOcrEngine
    }

    @Provides
    @Singleton
    @IntoSet
    fun providePaddleOcrContributor(
        contributor: PaddleOcrEngineContributor
    ): OcrEngineContributor {
        return contributor
    }
}
