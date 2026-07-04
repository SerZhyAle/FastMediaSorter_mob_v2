package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import android.graphics.Bitmap
import android.view.TextureView
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sza.fastmediasorter.data.repository.streams.StreamFrameCache
import com.sza.fastmediasorter.data.repository.streams.StreamFramePersistentStore
import com.sza.fastmediasorter.ui.player.helpers.StreamDataSourceFactoryProvider
import kotlinx.coroutines.CancellationException
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
 * S0675: captures one current frame per http(s) VIDEO live stream via a short-lived, muted ExoPlayer
 * using an "open -> first frame -> grab -> release" lifecycle. AUDIO and RTSP sources are out of scope
 * (the grid cell falls back to favicon/placeholder), so the engine stays in `src/main` with no
 * flavor-gated RTSP module. Each capture is bounded by [CAPTURE_TIMEOUT_MS]; results land in [cache].
 * The player and its offscreen surface are always torn down in a `finally`, so a hung stream never
 * leaks a decoder session or a graphics buffer.
 *
 * S0933: the frame is captured through a window-attached but off-screen [TextureView] rendered by the
 * muted ExoPlayer, then read via [TextureView.getBitmap] after the first decoded frame. This replaced an
 * offscreen [android.media.ImageReader] surface (S0700): that path threw [UnsupportedOperationException]
 * on a YUV-producing software codec (S0900) and, worse, native-killed the process during decoder/Surface
 * setup on some HW decoders (Samsung Exynos / API36) before any Java callback - uncatchable. A TextureView
 * renders through the normal GPU path, which did NOT native-crash on the S21 device test (2026-06-27); its
 * only prior failure was releasing before the first frame landed, fixed here by awaiting
 * onRenderedFirstFrame before the grab. [hostProvider] supplies the Activity-owned off-screen container
 * the transient capture view is added to; a null host (no streams UI) skips capture and the tile keeps
 * its favicon.
 */
@UnstableApi
class StreamFrameSnapshotManager(
    private val context: Context,
    private val cache: StreamFrameCache,
    private val scope: CoroutineScope,
    // S0712: persist each captured grid frame so a known channel shows its last frame on next launch.
    private val persistentStore: StreamFramePersistentStore,
    // S0933: window-attached off-screen ViewGroup hosting the transient capture TextureView (an Activity
    // view; null when no streams UI is attached, so capture is skipped and the favicon stays).
    private val hostProvider: () -> ViewGroup? = { null },
) {

    /** Invoked on the main thread after a successful capture so the adapter can repaint that url's tile. */
    var onCaptured: (url: String) -> Unit = {}

    /**
     * S0700: invoked on the main thread with the capture outcome for a captureable VIDEO stream - true when
     * a frame was decoded (reachable), false on timeout/error. Lets the grid refresh derive the green/red
     * status from the same decode that produces the thumbnail, instead of a separate reachability probe.
     */
    var onOutcome: (url: String, ok: Boolean) -> Unit = { _, _ -> }

    private val semaphore = Semaphore(MAX_CONCURRENT_CAPTURES)
    private val queue = ConcurrentLinkedQueue<String>()

    // Tracks enqueued urls so the same tile is not queued twice while a capture is pending.
    private val pending = HashSet<String>()
    private val inFlight = mutableListOf<Job>()

    /**
     * Enqueue a snapshot for [url]. Already-pending urls are always skipped; a still-fresh cached url is
     * skipped too unless [force] is set (an explicit pull-to-refresh re-captures even a fresh tile). The
     * capture renders into an offscreen surface this manager owns, so a recycled/scrolled cell never
     * aborts or misdirects the result (the adapter keys the repaint on the cached url, not on a View).
     */
    fun request(url: String, force: Boolean = false) {
        // S0700: entry point of the grid-capture flow (offscreen TextureView capture per visible tile).
        // Logged unconditionally (even while disabled below) so future test logs still show the call
        // site was reached - distinguishes "adapter never asked" from "asked but gated off".
        Timber.d("S0700: snapshot request enqueued for %s", url)
        // S0700/S0900 (2026-07-03): [CAPTURE_ENABLED] is the kill switch if this crashes again - real
        // capture killed the process natively on-device (Samsung SM-S731B, Android 16) regardless of
        // MAX_CONCURRENT_CAPTURES (tried 2, then 1, identical crash). See [capture]'s layout-settle-delay
        // comment for the current experiment and S0700's 2026-07-03 19:05 audit for the failed attempts.
        if (!CAPTURE_ENABLED || (!force && cache.isFresh(url))) return
        synchronized(pending) {
            if (!pending.add(url)) return
        }
        queue.add(url)
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
        val url = queue.poll() ?: return
        semaphore.withPermit {
            // cancelAll() clears `pending`; if our url is gone, the grid was left/stopped while we were
            // parked on the semaphore - skip the expensive ExoPlayer capture instead of launching it late.
            if (synchronized(pending) { url !in pending }) {
                Timber.d("S0900: capture skipped, grid left")
                return@withPermit
            }
            val job = scope.launch {
                val bitmap = capture(url)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) onCaptured(url)
                    onOutcome(url, bitmap != null)
                }
            }
            synchronized(inFlight) { inFlight.add(job) }
            try {
                job.join()
            } finally {
                synchronized(inFlight) { inFlight.remove(job) }
                synchronized(pending) { pending.remove(url) }
            }
        }
    }

    /**
     * Open [url] in a minimal muted ExoPlayer rendering into a transient off-screen [TextureView] added to
     * the [hostProvider] container, await the first decoded frame (or time out), grab it via
     * [TextureView.getBitmap], cache it, and release. Returns the bitmap, or null on no-host/timeout/error.
     * Everything runs on the main looper: ExoPlayer, the view add/remove, and the getBitmap grab.
     */
    suspend fun capture(url: String): Bitmap? = withContext(Dispatchers.Main) {
        val host = hostProvider()
        if (host == null) {
            Timber.i("Stream snapshot: no capture host - favicon fallback: %s", url)
            return@withContext null
        }
        Timber.d("S0933: TextureView capture start %s", url)
        var player: ExoPlayer? = null
        var textureView: TextureView? = null
        // true once the first frame is rendered, false on a player error; a timeout leaves it uncompleted.
        val firstFrame = CompletableDeferred<Boolean>()
        try {
            val tv = TextureView(host.context).apply {
                layoutParams = ViewGroup.LayoutParams(CAPTURE_WIDTH_PX, CAPTURE_HEIGHT_PX)
                // Kept drawn (so the SurfaceTexture receives frames) but pushed off the visible viewport.
                translationX = OFFSCREEN_TRANSLATION_PX
            }
            textureView = tv
            host.addView(tv)

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
            built.setVideoTextureView(tv)
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

            val rendered = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { firstFrame.await() }
            if (rendered != true) {
                Timber.i("Stream snapshot timed out or produced no frame: %s", url)
                return@withContext null
            }
            val bitmap = tv.getBitmap(CAPTURE_WIDTH_PX, CAPTURE_HEIGHT_PX)
            if (bitmap == null) {
                Timber.i("Stream snapshot TextureView returned no bitmap: %s", url)
                return@withContext null
            }
            cache.put(url, bitmap)
            // S0712: write the low-res thumbnail to disk off the main thread; best-effort, never blocks
            // the capture result. Reuses this manager's scope so it is cancelled with the grid.
            Timber.d("S0712: persist captured frame %s", url)
            scope.launch { persistentStore.save(url, bitmap) }
            bitmap
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.w(t, "Stream snapshot failed: %s", url)
            null
        } finally {
            // S0700/S0933 release contract: detach the video output before releasing the player, then
            // release the player and remove the transient view so no decoder session or view leaks.
            player?.setVideoTextureView(null)
            player?.release()
            textureView?.let { host.removeView(it) }
        }
    }

    private companion object {
        // S0933: gates all grid frame capture. Re-enabled with the TextureView path (see class KDoc) - the
        // offscreen-ImageReader engine native-killed the process on some HW decoders (S0700), which a Java
        // guard could not catch. A null [hostProvider] still skips capture, so it is a no-op without UI.
        const val CAPTURE_ENABLED = true

        // S0700: live HLS on a software decoder needs well over the old 6 s to fetch the manifest, pull a
        // segment, decode and render the first frame; 6 s timed out before any thumbnail appeared.
        const val CAPTURE_TIMEOUT_MS = 12_000L

        // One capture at a time keeps a single transient TextureView in the host and one live decoder
        // session during the grid's initial sweep.
        const val MAX_CONCURRENT_CAPTURES = 1

        // Thumbnail capture size (16:9). The decoder scales into the TextureView; the grid tile is
        // ~518x291 px on the test device, so this matches without over-allocating full-frame buffers.
        const val CAPTURE_WIDTH_PX = 640
        const val CAPTURE_HEIGHT_PX = 360

        // S0933: the capture TextureView stays drawn (SurfaceTexture keeps receiving frames) but is pushed
        // far off the visible viewport so it never appears over the grid.
        const val OFFSCREEN_TRANSLATION_PX = -10_000f
    }
}
