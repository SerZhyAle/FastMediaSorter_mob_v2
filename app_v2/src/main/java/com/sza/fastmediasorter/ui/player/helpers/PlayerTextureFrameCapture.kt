package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.view.TextureView
import androidx.media3.ui.PlayerView

/** Captures the TextureView-backed video surface without detaching it from playback. */
internal object PlayerTextureFrameCapture {

    @Suppress("TooGenericExceptionCaught")
    fun capture(
        playerView: PlayerView,
        width: Int? = null,
        height: Int? = null,
        onFailure: (Throwable) -> Unit = {},
    ): Bitmap? {
        val textureView = playerView.videoSurfaceView as? TextureView
        if (textureView == null || !textureView.isAvailable) return null
        return try {
            if (width != null && height != null) {
                textureView.getBitmap(width, height)
            } else {
                textureView.bitmap
            }
        } catch (t: Throwable) {
            onFailure(t)
            null
        }
    }
}
