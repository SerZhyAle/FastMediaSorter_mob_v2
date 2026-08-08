package com.sza.fastmediasorter.ui.dialog.helpers

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.sza.fastmediasorter.ui.player.helpers.StreamDataSourceFactoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * S1474: measures what a channel actually transmits.
 *
 * Modelled on `StreamHealthProbeManager` rather than inventing a second player lifecycle (ADR-2): the
 * same muted surfaceless engine, the same deadline, the same release in `finally` that also runs when the
 * window is dismissed mid-measurement. What differs is the answer - readiness there, a
 * [StreamMeasuredFormats] here.
 *
 * Two entry points, one extraction. [measure] opens its own connection; [readFrom] takes the values off an
 * engine that is already playing and never touches its state, because a server that allows one session per
 * client would drop the picture the user is watching if we opened a second one.
 */
@UnstableApi
class StreamFormatProbeManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    /** Exactly one measurement at a time: a second request supersedes the first rather than racing it. */
    private var job: Deferred<StreamMeasuredFormats>? = null

    /** True while a measurement is running, so the window can show its "measuring" state. */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Open [url] in a throwaway engine and report what it carries. Never throws for an unreachable channel:
     * a stream that could not be opened reports every field as unavailable, which is what the window shows.
     */
    suspend fun measure(url: String): StreamMeasuredFormats {
        cancel()
        val started = scope.async { probe(url) }
        job = started
        return try {
            started.await()
        } finally {
            if (job === started) job = null
        }
    }

    /**
     * Read the same values off an engine someone else owns.
     *
     * Deliberately not suspending and deliberately read-only - it must not prepare, start, stop, seek or
     * release the engine it is handed, because this manager did not open it and will not close it.
     */
    fun readFrom(player: Player): StreamMeasuredFormats = formatsFrom(
        videoFormat = selectedFormat(player.currentTracks, C.TRACK_TYPE_VIDEO),
        audioFormat = selectedFormat(player.currentTracks, C.TRACK_TYPE_AUDIO),
        videoSize = player.videoSize,
        bitrateEstimate = null,
    )

    /** Cancel a running measurement. Safe to call when idle, and safe to call twice. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun probe(url: String): StreamMeasuredFormats = withContext(Dispatchers.Main) {
        var player: ExoPlayer? = null
        var listener: Player.Listener? = null
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        var measured = StreamMeasuredFormats()
        val settled = CompletableDeferred<Unit>()
        try {
            val built = buildProbePlayer(bandwidthMeter)
            player = built
            built.volume = 0f
            val probeListener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                        measured = measured.mergedWith(readFrom(built))
                        if (!settled.isCompleted) settled.complete(Unit)
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    measured = measured.mergedWith(readFrom(built))
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Not an error worth raising: an unreachable channel is a legitimate answer here, and
                    // the window renders it as "not reported" rather than as a failure the user must act on.
                    Timber.i(error, "Stream format probe could not open %s", url)
                    if (!settled.isCompleted) settled.complete(Unit)
                }
            }
            listener = probeListener
            built.addListener(probeListener)
            built.setMediaItem(MediaItem.fromUri(url))
            built.prepare()
            built.playWhenReady = false
            withTimeoutOrNull(PROBE_TIMEOUT_MS) { settled.await() }
            measured.copy(observedBitrate = positiveOrNull(bandwidthMeter.bitrateEstimate))
        } catch (e: CancellationException) {
            // The window was dismissed mid-measurement. Cancellation is not a failure to report, and the
            // engine still closes in `finally` below.
            throw e
        } catch (e: IllegalStateException) {
            // Only construction and preparation reach here: a channel that simply cannot be played reports
            // a PlaybackException to the listener above and is a legitimate answer, not an exception.
            Timber.w(e, "Stream format probe could not be set up: %s", url)
            measured
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Stream format probe rejected the address: %s", url)
            measured
        } finally {
            // Paired explicitly rather than left to release(): the symmetry is what a reader and the
            // listener gate both check, and it holds on the cancellation path too.
            listener?.let { player?.removeListener(it) }
            player?.release()
        }
    }

    private fun buildProbePlayer(bandwidthMeter: DefaultBandwidthMeter): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, PLAYBACK_BUFFER_MS, REBUFFER_MS)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val dataSourceFactory = StreamDataSourceFactoryProvider.create(context)
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .build()
    }

    private fun selectedFormat(tracks: Tracks, trackType: Int): Format? = tracks.groups
        .firstOrNull { it.type == trackType && it.isSelected }
        ?.let { group ->
            (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat)
        }

    private companion object {
        /** Same budget the health sweep uses - long enough for a slow manifest, short enough not to hang. */
        const val PROBE_TIMEOUT_MS = 8_000L

        const val MIN_BUFFER_MS = 2_000
        const val MAX_BUFFER_MS = 8_000
        const val PLAYBACK_BUFFER_MS = 1_000
        const val REBUFFER_MS = 1_000
    }
}

/**
 * S1474: the pure half of the measurement - the part a unit test can reach without an engine.
 *
 * A field the engine never supplied stays null. `NO_VALUE` is the engine saying "not known", so it maps to
 * null too rather than to -1 reaching the window as a number.
 */
internal fun formatsFrom(
    videoFormat: Format?,
    audioFormat: Format?,
    videoSize: VideoSize?,
    bitrateEstimate: Long?,
): StreamMeasuredFormats = StreamMeasuredFormats(
    videoMimeType = videoFormat?.sampleMimeType,
    // Research artifact 01: a live manifest often declares no picture size until the first frame decodes,
    // and by then the engine knows it even though the format still does not.
    videoWidth = valueOrNull(videoFormat?.width) ?: valueOrNull(videoSize?.width),
    videoHeight = valueOrNull(videoFormat?.height) ?: valueOrNull(videoSize?.height),
    frameRate = videoFormat?.frameRate?.takeIf { it > 0f },
    videoBitrate = declaredBitrate(videoFormat),
    audioMimeType = audioFormat?.sampleMimeType,
    audioChannelCount = valueOrNull(audioFormat?.channelCount),
    audioSampleRate = valueOrNull(audioFormat?.sampleRate),
    audioBitrate = declaredBitrate(audioFormat),
    observedBitrate = bitrateEstimate?.let(::positiveOrNull),
)

/** Keeps whichever side has an answer, so a later reading never blanks a field an earlier one filled. */
internal fun StreamMeasuredFormats.mergedWith(next: StreamMeasuredFormats): StreamMeasuredFormats =
    StreamMeasuredFormats(
        videoMimeType = next.videoMimeType ?: videoMimeType,
        videoWidth = next.videoWidth ?: videoWidth,
        videoHeight = next.videoHeight ?: videoHeight,
        frameRate = next.frameRate ?: frameRate,
        videoBitrate = next.videoBitrate ?: videoBitrate,
        audioMimeType = next.audioMimeType ?: audioMimeType,
        audioChannelCount = next.audioChannelCount ?: audioChannelCount,
        audioSampleRate = next.audioSampleRate ?: audioSampleRate,
        audioBitrate = next.audioBitrate ?: audioBitrate,
        observedBitrate = next.observedBitrate ?: observedBitrate,
    )

/** Average first, peak second: the average is what the channel sustains, which is what the reader asked. */
private fun declaredBitrate(format: Format?): Int? =
    valueOrNull(format?.averageBitrate) ?: valueOrNull(format?.peakBitrate)

private fun valueOrNull(value: Int?): Int? = value?.takeIf { it != Format.NO_VALUE && it > 0 }

/**
 * Research artifact 02: the meter reports a non-positive estimate before it has seen enough traffic, and
 * showing that as "0 kbit/s" would read as a verdict on the channel rather than as "we do not know yet".
 */
private fun positiveOrNull(bitsPerSecond: Long): Long? = bitsPerSecond.takeIf { it > 0L }
