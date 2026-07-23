package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.delivery.DeliverableDescriptorCatalog
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSetContributor
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetContributor
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object LegacyBundledDeliverableSetsModule {

    // S0423: Translation (Set A) is bundled in the legacy base (the on-demand DFM was removed; the
    // legacy minSdk 23 split also broke the release bundle).
    // S0971: Set B (OCR/Tesseract) and Set D (FFmpeg DTS) are re-bundled - their `.so` ship in the APK
    // again (Play forbids downloading executable `.so`). Only Set C (audio-visualizations, `.mp4` data)
    // stays delivered on demand.
    @Provides
    @IntoSet
    fun contributor(): BundledDeliverableSetContributor = object : BundledDeliverableSetContributor {
        override fun bundledSets(): Set<DeliverableSet> = setOf(
            DeliverableSet.TRANSLATION,
            DeliverableSet.OCR_ENGINES,
            DeliverableSet.FFMPEG_DTS
        )
    }

    @Provides
    @IntoSet
    fun descriptorContributor(): DeliverableSetContributor = object : DeliverableSetContributor {
        override fun descriptors(): Map<DeliverableSet, DeliverableSourceDescriptor> = mapOf(
            DeliverableSet.AUDIO_VISUALIZATIONS to DeliverableDescriptorCatalog.audioVisualizations(),
            DeliverableSet.CHANNEL_PREVIEW_ATLAS to DeliverableDescriptorCatalog.channelPreviewAtlas()
        )
    }
}
