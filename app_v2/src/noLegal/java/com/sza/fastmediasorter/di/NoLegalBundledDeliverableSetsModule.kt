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
object NoLegalBundledDeliverableSetsModule {

    // Translation (Set A) stays bundled on sideload/VR (Google `.so` are not re-hosted).
    // S0971: Set B (OCR: Tesseract - S1703 withdrew PaddleOCR) and Set D (FFmpeg DTS) are re-bundled - their `.so` ship
    // in the APK again, so the app no longer depends on the GitHub download for them anywhere. Only
    // Set C (audio-visualizations, `.mp4` data) stays delivered on demand.
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
            DeliverableSet.CHANNEL_PREVIEW_ATLAS to DeliverableDescriptorCatalog.channelPreviewAtlas(),
            DeliverableSet.STREAM_LOGO_ATLAS to DeliverableDescriptorCatalog.streamLogoAtlas(),
            // S1971: Set E is deliberately absent from bundledSets() above. Listing it there would make
            // the capability report as installed and suppress the download, which is the exact opposite
            // of this ticket - the 44 MB leaving the APK is the whole point.
            DeliverableSet.VLC_ENGINE to DeliverableDescriptorCatalog.vlcEngine()
        )
    }
}
