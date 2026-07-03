package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.player.helpers.StreamDataSourceFactoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * S0700: on the "refresh" action, walks the currently-visible stream rows one by one and lightly probes
 * each for reachability - a surfaceless, muted ExoPlayer that just has to reach STATE_READY (the manifest
 * resolved and the first media bytes decoded) within [PROBE_TIMEOUT_MS]. A reachable stream records the
 * green OK status, an unreachable/timed-out one records the amber "unknown" status (red is reserved for a
 * real play the user started that failed), via [onStatus] -> RecordStreamPlayOutcomeUseCase.recordProbe.
 * Grid frame thumbnails are captured by the separate [StreamFrameSnapshotManager]; in grid mode that
 * capture also reports the video tile's status, so this sweep covers only the audio tiles there.
 *
 * The sweep is a single cancellable [Job]: any user interaction (tap, scroll, filter, leaving the screen)
 * calls [cancel] so the probe never fights the user or keeps decoding in the background. Probing is
 * sequential on purpose - one short-lived decoder at a time keeps the load off a budget device while the
 * user is still looking at the list.
 */
@UnstableApi
class StreamHealthProbeManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStatus: (id: String, ok: Boolean) -> Unit,
) {

    private var job: Job? = null

    /** True while a sweep is running, so the caller can show/stop a spinner. */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Start a fresh sweep over [sources] (typically the currently-visible rows). Cancels any in-flight
     * sweep first. [onComplete] runs on the main thread when the sweep finishes or is cancelled.
     */
    fun start(sources: List<StreamSourceEntity>, onComplete: () -> Unit = {}) {
        cancel()
        if (sources.isEmpty()) {
            onComplete()
            return
        }
        job = scope.launch {
            try {
                for (source in sources) {
                    if (!isActive) break
                    val ok = probe(source.url)
                    if (!isActive) break
                    onStatus(source.id, ok)
                }
            } finally {
                // NonCancellable so onComplete still runs when the sweep was cancelled - a plain
                // withContext(Main) throws in a cancelled coroutine before the block runs (KDoc contract).
                withContext(NonCancellable + Dispatchers.Main) {
                    Timber.d("S0900: health sweep onComplete (cancel-safe)")
                    onComplete()
                }
            }
        }
    }

    /** Cancel the running sweep (user interaction / leaving the screen). Safe to call when idle. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /**
     * Open [url] in a minimal muted, surfaceless ExoPlayer and resolve true once it reaches STATE_READY
     * (or ENDED), false on a player error or [PROBE_TIMEOUT_MS] timeout. playWhenReady stays false: a
     * reachability check only needs readiness, not actual playback. Always released in `finally`.
     */
    private suspend fun probe(url: String): Boolean = withContext(Dispatchers.Main) {
        var player: ExoPlayer? = null
        val ready = CompletableDeferred<Boolean>()
        try {
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
            built.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if ((state == Player.STATE_READY || state == Player.STATE_ENDED) && !ready.isCompleted) {
                        ready.complete(true)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.w(error, "Stream health probe error - marking unreachable: %s", url)
                    if (!ready.isCompleted) ready.complete(false)
                }
            })
            built.setMediaItem(MediaItem.fromUri(url))
            built.prepare()
            built.playWhenReady = false
            withTimeoutOrNull(PROBE_TIMEOUT_MS) { ready.await() } ?: run {
                Timber.i("Stream health probe timed out: %s", url)
                false
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.w(t, "Stream health probe failed: %s", url)
            false
        } finally {
            player?.release()
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 8_000L
    }
}
