package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
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
    // S1137: EXTENSION_RENDERER_MODE_ON (not _PREFER). The FFmpeg extension exists only for DTS/exotic
    // codecs the platform lacks (Set D above). _PREFER put FfmpegAudioRenderer ahead of MediaCodec for
    // every format it claims, so it intercepted AAC/HE-AAC radio streams and failed fatally mid-stream -
    // runtime decode errors bypass setEnableDecoderFallback (that only covers decoder init). _ON keeps
    // MediaCodec first for AAC/MP3/etc and falls through to FFmpeg only for formats the platform can't do.
    Timber.d("S1137: playback renderers factory - extension mode=ON (MediaCodec preferred for AAC)")
    return DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val infos = MediaCodecSelector.DEFAULT
                .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            // Keep this order paired with the ICY override in StreamDataSourceFactoryProvider.
            // AOSP priority alone did not stop the radio jumps, but it was part of the configuration
            // that passed the final real-device test. Reorder only, never filter, so unavailable AOSP
            // codecs fall back to the platform; video remains hardware-first.
            if (!MimeTypes.isAudio(mimeType)) return@setMediaCodecSelector infos
            infos.sortedBy(::audioDecoderPriority)
        }
}

private fun audioDecoderPriority(info: MediaCodecInfo): Int = when {
    info.name.startsWith(AOSP_CODEC_PREFIX) -> AOSP_CODEC_PRIORITY
    info.name.startsWith(SAMSUNG_CODEC_PREFIX) || info.name.startsWith(DOLBY_CODEC_PREFIX) ->
        VENDOR_CODEC_PRIORITY
    info.softwareOnly -> OTHER_SOFTWARE_CODEC_PRIORITY
    else -> PLATFORM_CODEC_PRIORITY
}

private const val AOSP_CODEC_PREFIX = "c2.android."
private const val SAMSUNG_CODEC_PREFIX = "c2.sec."
private const val DOLBY_CODEC_PREFIX = "c2.dolby."
private const val AOSP_CODEC_PRIORITY = 0
private const val OTHER_SOFTWARE_CODEC_PRIORITY = 1
private const val PLATFORM_CODEC_PRIORITY = 2
private const val VENDOR_CODEC_PRIORITY = 3

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
        entryPoint.deliveredNativeLibraryLoader().load(DeliverableSet.FFMPEG_DTS)
    } catch (e: Exception) {
        Timber.w(e, "FFmpeg DTS delivered-payload attach skipped")
    }
}
