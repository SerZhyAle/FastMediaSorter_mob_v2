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
object VrBundledDeliverableSetsModule {

    // Translation (Set A) stays bundled on sideload/VR (Google `.so` are not re-hosted). Set B (OCR:
    // Tesseract; vr ships no PaddleOCR), Set C (audio-visualizations) and Set D (FFmpeg DTS) are
    // de-bundled and delivered on demand (S0386 Phase 05).
    @Provides
    @IntoSet
    fun contributor(): BundledDeliverableSetContributor = object : BundledDeliverableSetContributor {
        override fun bundledSets(): Set<DeliverableSet> = setOf(
            DeliverableSet.TRANSLATION
        )
    }

    @Provides
    @IntoSet
    fun descriptorContributor(): DeliverableSetContributor = object : DeliverableSetContributor {
        override fun descriptors(): Map<DeliverableSet, DeliverableSourceDescriptor> = mapOf(
            DeliverableSet.AUDIO_VISUALIZATIONS to DeliverableDescriptorCatalog.audioVisualizations(),
            DeliverableSet.OCR_ENGINES to DeliverableDescriptorCatalog.ocrEnginesStore(),
            DeliverableSet.FFMPEG_DTS to DeliverableDescriptorCatalog.ffmpegDts()
        )
    }
}
