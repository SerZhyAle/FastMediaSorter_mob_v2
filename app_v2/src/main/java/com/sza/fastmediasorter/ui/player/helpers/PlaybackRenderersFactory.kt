package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory

fun createPlaybackRenderersFactory(context: Context): DefaultRenderersFactory {
    return DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
}