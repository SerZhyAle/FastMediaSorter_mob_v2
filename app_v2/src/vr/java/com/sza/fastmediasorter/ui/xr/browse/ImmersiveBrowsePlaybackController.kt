package com.sza.fastmediasorter.ui.xr.browse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import timber.log.Timber

/**
 * Owns in-process playback of the item selected in the immersive browser onto the main media quad,
 * mirroring [com.sza.fastmediasorter.ui.xr.DiagnosticXrActivity]'s proven decode/ExoPlayer approach
 * but self-contained so the diagnostic host stays untouched (S0773 ADR-1). One owner per ExoPlayer;
 * [stop] releases the player and surface deterministically.
 */
class ImmersiveBrowsePlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: DiagnosticXrRuntime,
) {

    private var exoPlayer: ExoPlayer? = null
    @Volatile private var reusableBuffer: ByteBuffer? = null

    /** Decodes an image within the heap budget and pushes it to the main quad. Returns success. */
    suspend fun playImage(file: File): Boolean {
        stop()
        runtime.setVideoSurfaceEnabled(false)
        val bitmap = decodeWithinBudget(file) ?: run {
            Timber.w("ImmersiveBrowsePlaybackController: decode failed for %s", file.name)
            return false
        }
        val bytes = copyToRgba(bitmap)
        if (bytes != null) {
            runtime.queueFrame(bytes, bitmap.width, bitmap.height)
        }
        returnToPool(bitmap)
        return bytes != null
    }

    /** Starts video playback on the native video surface. Returns success. */
    fun playVideo(uri: Uri): Boolean {
        stop()
        val surface = runtime.getVideoSurface()
        if (surface == null) {
            Timber.w("ImmersiveBrowsePlaybackController: native video surface not ready")
            return false
        }
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setVideoSurface(surface)
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "ImmersiveBrowsePlaybackController: playback error %s", error.errorCodeName)
                }
            })
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
        runtime.setVideoSurfaceEnabled(true)
        return true
    }

    /** Releases ExoPlayer + native video surface (S0773 audit: one owner, deterministic teardown). */
    fun stop() {
        runtime.setVideoSurfaceEnabled(false)
        exoPlayer?.apply {
            clearVideoSurface()
            stop()
            release()
        }
        exoPlayer = null
    }

    private suspend fun decodeWithinBudget(file: File): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val sample = pickSampleSize(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sample
        }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }
            .onFailure { Timber.w(it, "ImmersiveBrowsePlaybackController: decode threw for %s", file.name) }
            .getOrNull()
    }

    // Smallest power-of-2 sample that keeps the ARGB_8888 footprint within MAX_DECODE_BYTES (S0772 OOM).
    private fun pickSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (sample < MAX_SAMPLE) {
            val bytes = (width / sample).toLong() * (height / sample).toLong() * RGBA_BYTES_PER_PIXEL
            if (bytes <= MAX_DECODE_BYTES) return sample
            sample *= 2
        }
        return sample
    }

    // Direct-buffer intermediate: heap ByteBuffer.wrap yielded all-zero pixels on-device (S0960).
    private fun copyToRgba(bitmap: Bitmap): ByteArray? {
        val size = bitmap.width * bitmap.height * RGBA_BYTES_PER_PIXEL
        val buffer = reusableBuffer?.takeIf { it.capacity() >= size }?.apply { clear() }
            ?: ByteBuffer.allocateDirect(size).also { reusableBuffer = it }
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        return runCatching {
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            bytes
        }.getOrElse {
            Timber.e(it, "ImmersiveBrowsePlaybackController: RGBA copy degraded (%d bytes)", size)
            null
        }
    }

    private fun returnToPool(bitmap: Bitmap) {
        runCatching { Glide.get(context).bitmapPool.put(bitmap) }
            .onFailure { bitmap.recycle() }
    }

    private companion object {
        const val RGBA_BYTES_PER_PIXEL = 4
        const val MAX_DECODE_BYTES = 96L * 1024L * 1024L
        const val MAX_SAMPLE = 8
    }
}
