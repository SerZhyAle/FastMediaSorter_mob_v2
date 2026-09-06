package com.sza.fastmediasorter.ui.stopwatch.helpers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService
import timber.log.Timber

/**
 * Everything the accompaniment needs from the platform, behind one seam.
 *
 * [StopwatchAudioManager] decides *when* a run has music; this decides *how* a device makes a sound.
 * Splitting the two is what lets the ownership rules be tested without a framework player - strategic
 * §7 names a second playback point drifting from the existing one as the live risk of this phase.
 */
interface StopwatchAudioOutput {

    /** Highest value the music stream accepts, or 0 where the device exposes no music stream. */
    val maxStreamVolume: Int

    /** Level of the music stream - the same stream the screen's slider shows (S1411 phase 04). */
    var streamVolume: Int

    fun prepare(uri: Uri)

    fun start()

    fun stop()

    fun release()
}

/**
 * Keeps the accompaniment in step with the measurement (S1411 §5.2, §6.2).
 *
 * Every entry point is idempotent, because it is driven by the collected screen state rather than by a
 * button handler: a run started by a tap, a keyboard or a volume key behaves identically, and four
 * participants starting one after another still produce exactly one [StopwatchAudioOutput.start].
 */
class StopwatchAudioManager(
    private val output: StopwatchAudioOutput,
) {

    private var trackUri: Uri? = null
    private var playing = false
    private var released = false

    val maxStreamVolume: Int
        get() = output.maxStreamVolume

    var streamVolume: Int
        get() = output.streamVolume
        set(value) {
            output.streamVolume = value
        }

    /** Hands the companion the track to play. A null uri leaves the run silent rather than failing. */
    fun selectTrack(uri: Uri?) {
        if (released || uri == trackUri) return
        stopPlayback()
        trackUri = uri
        uri?.let(output::prepare)
    }

    /** Plays while any participant runs, silent otherwise. Safe on every state emission. */
    fun syncWith(anyRunning: Boolean) {
        if (released) return
        if (anyRunning) {
            startPlayback()
        } else {
            stopPlayback()
        }
    }

    /**
     * Releases the player, which Rule 18 requires the moment the screen stops using it. Calling this
     * twice is safe: the second call has nothing left to release.
     */
    fun release() {
        if (released) return
        stopPlayback()
        released = true
        output.release()
    }

    private fun startPlayback() {
        if (playing || trackUri == null) return
        playing = true
        output.start()
    }

    private fun stopPlayback() {
        if (!playing) return
        playing = false
        output.stop()
    }
}

/**
 * The output a real device gets: one [MediaPlayer] on the music stream, holding transient audio focus
 * only for as long as it plays.
 *
 * Preparation is asynchronous and a stop pauses rather than tears down, so a series of runs re-uses one
 * prepared player instead of re-reading the file between them.
 */
class MediaPlayerStopwatchAudioOutput(
    private val context: Context,
) : StopwatchAudioOutput {

    private val audioManager: AudioManager? = context.getSystemService()

    private var player: MediaPlayer? = null
    private var prepared = false
    private var startWhenPrepared = false
    private var pausedByFocusLoss = false
    private var focusRequest: AudioFocusRequest? = null
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null

    override val maxStreamVolume: Int
        get() = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    override var streamVolume: Int
        get() = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        set(value) {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        }

    override fun prepare(uri: Uri) {
        release()
        player = MediaPlayer().apply {
            setAudioAttributes(MUSIC_ATTRIBUTES)
            // A run outlasting the track keeps its accompaniment instead of falling silent halfway.
            isLooping = true
            setOnPreparedListener(::onPrepared)
            setOnErrorListener { _, what, extra -> onPlaybackError(what, extra) }
            runCatching {
                setDataSource(context, uri)
                prepareAsync()
            }.onFailure { error ->
                // The chosen track may have been deleted or had its permission revoked since it was
                // picked. The screen keeps measuring in silence, which is the safe default here.
                Timber.w(error, "Stopwatch companion could not open the chosen track")
            }
        }
    }

    override fun start() {
        val current = player ?: return
        if (!requestFocus()) return
        if (prepared) {
            current.start()
        } else {
            startWhenPrepared = true
        }
    }

    override fun stop() {
        startWhenPrepared = false
        pausedByFocusLoss = false
        val current = player
        if (prepared && current != null && current.isPlaying) {
            current.pause()
            current.seekTo(0)
        }
        abandonFocus()
    }

    override fun release() {
        abandonFocus()
        prepared = false
        startWhenPrepared = false
        pausedByFocusLoss = false
        player?.release()
        player = null
    }

    private fun onPrepared(preparedPlayer: MediaPlayer) {
        prepared = true
        if (startWhenPrepared) {
            startWhenPrepared = false
            preparedPlayer.start()
        }
    }

    /** Returns true so [MediaPlayer] treats the error as handled and does not report it again. */
    private fun onPlaybackError(what: Int, extra: Int): Boolean {
        Timber.w("Stopwatch companion playback failed: what=%d extra=%d", what, extra)
        prepared = false
        startWhenPrepared = false
        return true
    }

    /**
     * A transient loss - a navigation prompt, an incoming call - pauses and waits for the gain that
     * `AUDIOFOCUS_GAIN_TRANSIENT` promises. Ending the run's music on the first interruption instead
     * would leave the rest of the measurement silent with nothing on screen saying why.
     */
    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> resumeAfterFocusLoss()
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            else -> pauseForFocusLoss()
        }
    }

    private fun pauseForFocusLoss() {
        val current = player ?: return
        if (prepared && current.isPlaying) {
            current.pause()
            pausedByFocusLoss = true
        }
    }

    private fun resumeAfterFocusLoss() {
        val current = player ?: return
        if (pausedByFocusLoss && prepared) {
            pausedByFocusLoss = false
            current.start()
        }
    }

    private fun requestFocus(): Boolean {
        val manager = audioManager ?: return false
        val listener = focusListener ?: AudioManager.OnAudioFocusChangeListener(::onFocusChange)
        focusListener = listener
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(MUSIC_ATTRIBUTES)
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        val manager = audioManager ?: return
        val listener = focusListener ?: return
        // The request object itself is abandoned, not an equivalent rebuilt one, so the framework
        // matches the grant it actually issued.
        val request = focusRequest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && request != null) {
            manager.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(listener)
        }
        focusRequest = null
        focusListener = null
    }

    private companion object {
        val MUSIC_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
