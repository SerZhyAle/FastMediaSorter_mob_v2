package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.sza.fastmediasorter.data.delivery.DeliveredNativeLibraryLoader
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DeliveryRenderersEntryPoint {
    fun deliveredNativeLibraryLoader(): DeliveredNativeLibraryLoader
    fun deliverableCapabilityRepository(): DeliverableCapabilityRepository
}

fun createPlaybackRenderersFactory(context: Context): DefaultRenderersFactory {
    // S0386 Phase 07: attach the delivered FFmpeg DTS `.so` (Set D) before the renderers factory is
    // built, so it is on the classloader path when media3's FfmpegLibrary loads `ffmpegJNI`.
    attachDeliveredFfmpegDtsIfInstalled(context)
    return DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
}

/**
 * When the FFmpeg DTS set (Set D) was delivered on demand, splice its payload into the classloader
 * native search path so DTS decoding becomes available. If the set is not installed - or the attach
 * fails - media3 simply reports the decoder unavailable and playback degrades gracefully (no crash).
 * Bundled flavors (where FFMPEG_DTS is still in the base) short-circuit inside the loader.
 */
private fun attachDeliveredFfmpegDtsIfInstalled(context: Context) {
    try {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DeliveryRenderersEntryPoint::class.java
        )
        if (!entryPoint.deliverableCapabilityRepository().isInstalledBlocking(DeliverableSet.FFMPEG_DTS)) {
            return
        }
        Timber.d("S0386: FFmpeg DTS (Set D) installed - attaching delivered decoder before renderers build")
        entryPoint.deliveredNativeLibraryLoader().load(DeliverableSet.FFMPEG_DTS)
    } catch (e: Exception) {
        Timber.w(e, "FFmpeg DTS delivered-payload attach skipped")
    }
}
