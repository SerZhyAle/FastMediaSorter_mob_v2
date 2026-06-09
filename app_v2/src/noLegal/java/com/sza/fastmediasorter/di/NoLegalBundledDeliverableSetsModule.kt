package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSetContributor
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object NoLegalBundledDeliverableSetsModule {

    @Provides
    @IntoSet
    fun contributor(): BundledDeliverableSetContributor = object : BundledDeliverableSetContributor {
        override fun bundledSets(): Set<DeliverableSet> = setOf(
            DeliverableSet.TRANSLATION,
            DeliverableSet.OCR_ENGINES,
            DeliverableSet.AUDIO_VISUALIZATIONS,
            DeliverableSet.FFMPEG_DTS
        )
    }
}