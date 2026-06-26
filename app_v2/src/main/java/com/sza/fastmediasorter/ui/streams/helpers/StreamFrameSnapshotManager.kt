package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import android.graphics.Bitmap
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sza.fastmediasorter.data.repository.streams.StreamFrameCache
import com.sza.fastmediasorter.ui.player.helpers.StreamDataSourceFactoryProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * S0675: captures one current frame per http(s) VIDEO live stream via a short-lived, muted, texture-
 * rendered ExoPlayer using an "open -> first frame -> grab -> release" lifecycle. AUDIO and RTSP sources
 * are out of scope (the grid cell falls back to favicon/placeholder), so the engine stays in `src/main`
 * with no flavor-gated RTSP module. Each capture is bounded by [CAPTURE_TIMEOUT_MS]; results land in
 * [cache]. The player is always released in a `finally`, so a hung stream never leaks a decoder session.
 */
@UnstableApi
class StreamFrameSnapshotManager(
    private val context: Context,
    private val cache: StreamFrameCache,
    private val scope: CoroutineScope,
) {

    private data class CaptureRequest(val url: String, val textureViewProvider: () -> TextureView?)

    /** Invoked on the main thread after a successful capture so the adapter can repaint that url's tile. */
    var onCaptured: (url: String) -> Unit = {}

    /**
     * S0700: invoked on the main thread with the capture outcome for a captureable VIDEO stream - true when
     * a frame was decoded (reachable), false on timeout/error. Lets the grid refresh derive the green/red
     * status from the same decode that produces the thumbnail, instead of a separate reachability probe.
     */
    var onOutcome: (url: String, ok: Boolean) -> Unit = { _, _ -> }

    private val semaphore = Semaphore(MAX_CONCURRENT_CAPTURES)
    private val queue = ConcurrentLinkedQueue<CaptureRequest>()

    // Tracks enqueued urls so the same tile is not queued twice while a capture is pending.
    private val pending = HashSet<String>()
    private val inFlight = mutableListOf<Job>()

    /**
     * Enqueue a snapshot for [url], resolving the [textureViewProvider] lazily at drain time (the cell
     * may have been recycled by then). Fresh-cached urls and already-pending urls are skipped.
     */
    fun request(url: String, textureViewProvider: () -> TextureView?) {
        if (cache.isFresh(url)) return
        synchronized(pending) {
            if (!pending.add(url)) return
        }
        queue.add(CaptureRequest(url, textureViewProvider))
        scope.launch { drainOne() }
    }

    /** Clear the queue and cancel in-flight captures (leaving GRID / on stop). */
    fun cancelAll() {
        queue.clear()
        synchronized(pending) { pending.clear() }
        synchronized(inFlight) {
            inFlight.forEach { it.cancel() }
            inFlight.clear()
        }
    }

    private suspend fun drainOne() {
        val req = queue.poll() ?: return
        semaphore.withPermit {
            val job = scope.launch {
                val textureView = withContext(Dispatchers.Main) { req.textureViewProvider() }
                if (textureView == null) {
                    Timber.i("Stream snapshot skipped - cell recycled: %s", req.url)
                } else {
                    val bitmap = capture(req.url, textureView)
                    // S0700: a recycled cell is not an outcome; only a real decode attempt reports green/red.
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) onCaptured(req.url)
                        onOutcome(req.url, bitmap != null)
                    }
                }
            }
            synchronized(inFlight) { inFlight.add(job) }
            try {
                job.join()
            } finally {
                synchronized(inFlight) { inFlight.remove(job) }
                synchronized(pending) { pending.remove(req.url) }
            }
        }
    }

    /**
     * Open [url] in a minimal muted ExoPlayer rendering into [textureView], await the first decoded
     * frame (or time out), grab the bitmap on the main thread, cache it, and release. Returns the
     * bitmap, or null on timeout/error. ExoPlayer must be built and driven on the main looper; the
     * frame grab happens there too.
     */
    suspend fun capture(url: String, textureView: TextureView): Bitmap? = withContext(Dispatchers.Main) {
        var player: ExoPlayer? = null
        val firstFrame = CompletableDeferred<Boolean>()
        try {
            // Smaller live buffer than the full-screen stream player: a snapshot only needs the first
            // decoded frame, so a long backlog just wastes RAM/network on a budget device.
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_000, 8_000, 1_000, 1_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            val dataSourceFactory = StreamDataSourceFactoryProvider.create(context)
            val built = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
                .build()
            player = built
            built.volume = 0f
            built.setVideoTextureView(textureView)
            built.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (!firstFrame.isCompleted) firstFrame.complete(true)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.w(error, "Stream snapshot error - falling back to favicon: %s", url)
                    if (!firstFrame.isCompleted) firstFrame.complete(false)
                }
            })
            val liveConfiguration = MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(10_000)
                .setMinOffsetMs(4_000)
                .setMaxOffsetMs(20_000)
                .setMaxPlaybackSpeed(1.02f)
                .build()
            built.setMediaItem(MediaItem.Builder().setUri(url).setLiveConfiguration(liveConfiguration).build())
            built.prepare()
            built.playWhenReady = true

            val rendered = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { firstFrame.await() } == true
            if (!rendered) {
                Timber.i("Stream snapshot timed out before first frame: %s", url)
                return@withContext null
            }
            val bitmap = textureView.bitmap
            if (bitmap == null) {
                Timber.i("Stream snapshot produced no bitmap: %s", url)
                return@withContext null
            }
            cache.put(url, bitmap)
            bitmap
        } catch (t: Throwable) {
            Timber.w(t, "Stream snapshot failed: %s", url)
            null
        } finally {
            player?.release()
        }
    }

    private companion object {
        // S0700: live HLS on a software decoder needs well over the old 6 s to fetch the manifest, pull a
        // segment, decode and render the first frame; 6 s timed out before any thumbnail appeared.
        const val CAPTURE_TIMEOUT_MS = 12_000L
        const val MAX_CONCURRENT_CAPTURES = 2
    }
}
