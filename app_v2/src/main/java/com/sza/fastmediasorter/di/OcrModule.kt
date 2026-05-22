package com.sza.fastmediasorter.di

import android.content.Context
import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngine
import com.sza.fastmediasorter.ui.player.helpers.TesseractManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OcrModule {

    @Provides
    @Singleton
    fun provideOfflineOcrEngine(
        @ApplicationContext context: Context
    ): OfflineOcrEngine {
        return TesseractManager(context)
    }
}
