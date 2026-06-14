package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.ocr.NoOpOfflineOcrEngine
import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * OCR-disabled flavors (photos, lite) bind a no-op default engine. The Tesseract-backed module of
 * the same name lives in src/ocrEnabled; exactly one is mounted per flavor.
 */
@Module
@InstallIn(SingletonComponent::class)
object OcrModule {

    @Provides
    @Singleton
    fun provideOfflineOcrEngine(): OfflineOcrEngine = NoOpOfflineOcrEngine()
}
