package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
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
import kotlinx.coroutines.delay
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
 * S0700: the capture is decoupled from the RecyclerView view hierarchy. The grid cell's TextureView
 * was released together with the cell ~450 ms after the player started - before the first HLS segment
 * decoded onto its SurfaceTexture - so onRenderedFirstFrame never fired and the tile stayed black. The
 * player now renders into an offscreen [ImageReader] surface owned by this manager (not any View); the
 * first decoded frame is read straight off the reader into a Bitmap and cached, and the grid adapter
 * repaints the tile from the cache. ImageReader (not a bare SurfaceTexture + PixelCopy) is used because
 * reading back a SurfaceTexture-backed Surface needs a GL context + glReadPixels, whereas an
 * ImageReader hands the decoded RGBA buffer to the CPU directly with no GL plumbing.
 *
 * S0900: the RGBA readback only works on codecs that render RGBA_8888 into the reader. Some decoders
 * push YUV_420_888 instead, and the acquire throws [UnsupportedOperationException] on the reader thread
 * (a hard process kill before this was caught). Such frames are dropped so capture() falls back to the
 * channel favicon/flag rather than crashing - a live thumbnail is simply unavailable on those devices.
 */
@UnstableApi
class StreamFrameSnapshotManager(
    private val context: Context,
    private val cache: StreamFrameCache,
    private val scope: CoroutineScope,
    // S0712: persist each captured grid frame so a known channel shows its last frame on next launch.
    private val persistentStore: StreamFramePersistentStore,
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
        // S0700: entry point of the grid-capture flow (offscreen ImageReader capture per visible tile).
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
     * Open [url] in a minimal muted ExoPlayer that renders into an offscreen [ImageReader], await the
     * first decoded frame (or time out), copy that frame into a Bitmap, cache it, and release. Returns
     * the bitmap, or null on timeout/error. ExoPlayer must be built and driven on the main looper; the
     * ImageReader callback runs on its own handler, the Bitmap copy is CPU-side and View-free.
     */
    suspend fun capture(url: String): Bitmap? = withContext(Dispatchers.Main) {
        var player: ExoPlayer? = null
        var imageReader: ImageReader? = null
        var surface: Surface? = null
        val firstFrame = CompletableDeferred<Bitmap?>()
        try {
            // S0700/S0900 experiment (2026-07-03): a native process kill reproduced on-device (Samsung
            // SM-S731B, Android 16) the instant the FIRST capture of a GRID session builds its
            // ImageReader+ExoPlayer, independent of MAX_CONCURRENT_CAPTURES (tried 2, then 1 - identical
            // crash both times). Both attempts happen synchronously right on top of the grid's own
            // RecyclerView layout/bind burst (19 tiles laid out in the same frame the grid appears).
            // Untested variable: a Surface/BufferQueue setup race with that layout burst, not a per-attempt
            // incompatibility. This delay pushes the real ImageReader/ExoPlayer setup safely past the
            // grid's initial layout+draw settling before doing anything risky. If this still crashes,
            // stop guessing blind - get a real native trace (tombstone/adb logcat) before another attempt.
            delay(POST_LAYOUT_SETTLE_DELAY_MS)
            // A fixed-size offscreen reader: the decoder scales each frame into the thumbnail size, so the
            // capture never allocates full-resolution buffers and needs no late resize on the real size.
            val reader = ImageReader.newInstance(
                CAPTURE_WIDTH_PX,
                CAPTURE_HEIGHT_PX,
                PixelFormat.RGBA_8888,
                MAX_BUFFERED_IMAGES,
            )
            imageReader = reader
            val readerSurface = reader.surface
            surface = readerSurface
            reader.setOnImageAvailableListener({ r ->
                // This callback runs on the ImageReader's own HandlerThread, where ANY uncaught throw
                // kills the whole process (the GRID crash: an acquire on a YUV producer threw here).
                // capture()'s own try/catch only guards the main-thread coroutine, not this thread, so the
                // entire body is wrapped: every failure becomes a logged null-frame and capture() falls
                // back to the channel favicon instead of crashing.
                try {
                    if (firstFrame.isCompleted) {
                        // S0875: dispatched message can outlive capture()'s main-thread reader.close();
                        // the reader-closed race is benign and stays a quiet debug, not a warning.
                        try {
                            r.acquireLatestImage()?.close()
                        } catch (e: IllegalStateException) {
                            Timber.d(e, "Stream snapshot acquire raced reader close - drop frame")
                        }
                        return@setOnImageAvailableListener
                    }
                    val bitmap = readFrame(r)
                    if (!firstFrame.isCompleted) firstFrame.complete(bitmap)
                } catch (t: Throwable) {
                    Timber.w(t, "Stream snapshot callback failed on reader thread - favicon fallback")
                    if (!firstFrame.isCompleted) firstFrame.complete(null)
                }
            }, readerHandler)

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
            built.setVideoSurface(readerSurface)
            built.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Timber.w(error, "Stream snapshot error - falling back to favicon: %s", url)
                    if (!firstFrame.isCompleted) firstFrame.complete(null)
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

            val bitmap = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { firstFrame.await() }
            if (bitmap == null) {
                Timber.i("Stream snapshot timed out or produced no frame: %s", url)
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
            // S0700 release contract: detach the surface before releasing the player, then release the
            // player, the Surface and the ImageReader so no decoder session or graphics buffer leaks.
            player?.setVideoSurface(null)
            player?.release()
            surface?.release()
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        }
    }

    /**
     * Copy the latest decoded frame out of [reader] into an ARGB_8888 Bitmap. The RGBA_8888 plane can be
     * row-padded (rowStride > width * pixelStride), so the buffer is read into a padded bitmap and cropped
     * back to the capture size. Returns null if no image is available or the copy fails.
     */
    private fun readFrame(reader: ImageReader): Bitmap? {
        // S0875: acquire raced main-thread capture() teardown (reader closed) - benign, skip this frame.
        val image = try {
            reader.acquireLatestImage()
        } catch (e: IllegalStateException) {
            Timber.d(e, "Stream snapshot acquire raced reader close - drop frame")
            null
        } catch (e: UnsupportedOperationException) {
            // S0900: some codecs render YUV_420_888 (0x23) into the surface while this reader is
            // RGBA_8888 (0x1); acquire then throws on the ImageReader thread and killed the process.
            // Treat as an unusable frame so capture() times out into the favicon fallback, never crashes.
            Timber.i(e, "Stream snapshot: producer frame format unsupported by RGBA reader - favicon fallback")
            null
        } ?: return null
        return try {
            val plane = image.planes.firstOrNull()
            if (plane == null) {
                null
            } else {
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val paddedWidth = image.width + rowPadding / pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                if (rowPadding == 0) {
                    padded
                } else {
                    val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    if (cropped !== padded) padded.recycle()
                    cropped
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "Stream snapshot frame readback failed")
            null
        } finally {
            image.close()
        }
    }

    private companion object {
        // S0700 (2026-07-04): kill switch for [request]. DISABLED - root cause now known from real logs and
        // the ImageReader capture path is not viable on all hardware:
        //   - Emulator/software codec: the decoder renders YUV_420_888 (0x23) into this RGBA_8888 (0x1)
        //     reader, so acquire throws a Java UnsupportedOperationException (caught in [readFrame], but the
        //     frame is unusable -> favicon fallback anyway).
        //   - Samsung Exynos / Android 16 (API 36): a NATIVE process kill during the decoder/Surface setup,
        //     before any Java callback - the session log dies mid enqueue-burst with no exception. A Java
        //     try/catch cannot catch a native abort, so capture cannot be made safe by guarding alone.
        // Until capture is reworked to a mechanism that survives both (e.g. YUV->RGB, or the older
        // TextureView + PixelCopy path that did not native-crash on the S21), the grid uses the favicon
        // atlas (S0785) as the thumbnail. Re-enable only together with that rework.
        const val CAPTURE_ENABLED = false

        // S0700/S0900 (2026-07-03): delay before the risky ImageReader/ExoPlayer setup in [capture] - see
        // the WHY comment there. Long enough to clear a couple of frames past the grid's initial
        // layout/draw burst, short enough not to meaningfully add to the up-to-CAPTURE_TIMEOUT_MS wait for
        // a thumbnail to appear.
        const val POST_LAYOUT_SETTLE_DELAY_MS = 500L

        // S0700: live HLS on a software decoder needs well over the old 6 s to fetch the manifest, pull a
        // segment, decode and render the first frame; 6 s timed out before any thumbnail appeared.
        const val CAPTURE_TIMEOUT_MS = 12_000L

        // S0900: device logs (Samsung SM-S731B, Android 16) showed a native process kill the instant GRID
        // opened with 2 concurrent muted ExoPlayer sessions decoding live HLS into an offscreen ImageReader -
        // no Java exception, no crash-report file (LoggingHelper's uncaught-handler never fired), only the
        // enqueue log line before the process died. Dropping to 1-at-a-time did NOT stop the crash (see
        // S0700 2026-07-03 19:05 audit) - concurrency is ruled out as the cause. Left at 1 (harmless) since
        // [CAPTURE_ENABLED] gates all real capture off regardless until root-caused.
        const val MAX_CONCURRENT_CAPTURES = 1

        // Thumbnail capture size (16:9). The decoder scales into this offscreen reader; the grid tile is
        // ~518x291 px on the test device, so this matches without over-allocating full-frame buffers.
        const val CAPTURE_WIDTH_PX = 640
        const val CAPTURE_HEIGHT_PX = 360
        const val MAX_BUFFERED_IMAGES = 2

        // ImageReader callbacks (and the CPU-side pixel copy) run on a dedicated background looper so the
        // ~1 MB RGBA readback never touches the main thread. One shared thread serves all captures.
        val readerHandler: Handler =
            Handler(HandlerThread("StreamFrameReader").apply { start() }.looper)
    }
}
