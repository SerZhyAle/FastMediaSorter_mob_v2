package com.sza.fastmediasorter.ui.xr.helpers

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import com.sza.fastmediasorter.core.xr.PlayerStateSnapshot
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import com.sza.fastmediasorter.ui.player.helpers.PrefetchLoadControlFactory
import timber.log.Timber
import java.io.File

/**
 * S0989: owns the immersive diagnostic ExoPlayer instance, its [Player.Listener], and the ordered
 * teardown, extracted from DiagnosticXrActivity. Not to be confused with [HudPlaybackController],
 * the transport-button wrapper - this one builds and releases the player itself.
 *
 * The host wires behaviour via callbacks: [onError] paints the error banner + toast (the controller
 * releases the player right after, preserving the original order), [onTracksChanged] refreshes the
 * panel track rows, [onCues] feeds the subtitle quad, and [onPlayerChanged] mirrors the player into
 * [HudPlaybackController] and seeds/clears the panel model.
 */
class VrDiagnosticPlaybackController(
    private val context: Context,
    private val runtime: DiagnosticXrRuntime,
    private val snapshotProvider: () -> PlayerStateSnapshot?,
    private val runOnUiThread: (() -> Unit) -> Unit,
    private val isHostActive: () -> Boolean,
    private val onSurfaceUnavailable: () -> Unit,
    private val onError: (file: File, shortErr: String) -> Unit,
    private val onTracksChanged: () -> Unit,
    private val onCues: (CueGroup) -> Unit,
    private val onPlayerChanged: (ExoPlayer?) -> Unit,
) {

    var player: ExoPlayer? = null
        private set

    // S1640: the listener closes over the file being started, so it is built per start rather than once
    // for the class; the field exists only to give the paired removal in release() an addressee.
    private var playerListener: Player.Listener? = null

    fun start(file: File): Boolean {
        release()
        val videoSurface = runtime.getVideoSurface()
        if (videoSurface == null) {
            Timber.w("DiagnosticXrActivity: native video surface is not ready for ${file.name}")
            onSurfaceUnavailable()
            return false
        }
        val snapshot = snapshotProvider()
        // S1113: immersive playback was on the DEFAULT LoadControl (no S0772 heap cap), so a 7K
        // HEVC stream over-buffers on the 512 MB-heap headset and stalls (blank quad). Apply the
        // same heap-bounded LoadControl the flat player uses.
        val vrPlayer = ExoPlayer.Builder(context)
            .setLoadControl(PrefetchLoadControlFactory.build(plan = null, tag = "vr-diagnostic"))
            .build()
        player = vrPlayer.apply {
            setVideoSurface(videoSurface)
            repeatMode = Player.REPEAT_MODE_ALL

            // S1640: declared inside this apply block on purpose - the object literal's enclosing
            // receiver stays the player, so the unqualified release() call further down keeps binding
            // to the player exactly as before.
            // S1662: that binding was the defect, and it is now fixed at the call site with an explicit
            // receiver rather than by moving this declaration - keeping it here preserves the closure
            // over `file` that S1640's per-start construction depends on.
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause
                    val stage = when {
                        error.errorCode in 2000..2999 -> "source preparation"
                        error.errorCode in 4001..4002 || error.errorCode == 4004 -> "decoder initialization"
                        error.errorCode == 4003 -> "render"
                        else -> {
                            val exoEx = error as? ExoPlaybackException
                            when (exoEx?.type) {
                                ExoPlaybackException.TYPE_SOURCE -> "source preparation"
                                ExoPlaybackException.TYPE_RENDERER -> "decoder initialization / render"
                                else -> "unknown stage"
                            }
                        }
                    }

                    val decoderDetails = StringBuilder()
                    if (cause is MediaCodecRenderer.DecoderInitializationException) {
                        decoderDetails.append("codec=${cause.codecInfo?.name ?: "null"}, ")
                        decoderDetails.append("mime=${cause.mimeType ?: "null"}, ")
                        decoderDetails.append("diag=${cause.diagnosticInfo ?: "null"}")
                    } else if (cause != null) {
                        decoderDetails.append("cause=${cause.javaClass.simpleName}: ${cause.message}")
                        cause.cause?.let { inner ->
                            decoderDetails.append(" -> ${inner.javaClass.simpleName}: ${inner.message}")
                        }
                    } else {
                        decoderDetails.append("no cause info")
                    }

                    Timber.e(
                        error,
                        "VR diagnostic playback failed! File: ${file.name}, Stage: $stage, " +
                            "Code: ${error.errorCode} (${error.errorCodeName}), " +
                            "Msg: ${error.message}, $decoderDetails"
                    )

                    runOnUiThread {
                        if (isHostActive()) {
                            val shortErr = "${error.errorCodeName} ($stage)"
                            onError(file, shortErr)

                            Toast.makeText(
                                context,
                                "Playback Error: $shortErr",
                                Toast.LENGTH_LONG
                            ).show()

                            // S1662: the receiver is spelled out because the nearest implicit one here
                            // is the ExoPlayer of the enclosing apply, not this controller. Unqualified,
                            // this call released the player alone and skipped the rest of the teardown:
                            // the native video surface stayed enabled, the HUD kept mirroring a dead
                            // player, and the field still pointed at the released instance, so the next
                            // start() reached into it.
                            this@VrDiagnosticPlaybackController.release()
                        }
                    }
                }

                // S0964: tracks become known only after prepare(); refresh the panel rows when
                // they land or when a selection override is applied.
                override fun onTracksChanged(tracks: Tracks) {
                    runOnUiThread {
                        if (!isHostActive()) return@runOnUiThread
                        onTracksChanged()
                    }
                }

                // S0986: feed the selected subtitle track's cues to the lower-third subtitle quad.
                // Cue callbacks arrive on the main thread; the controller renders + uploads there.
                override fun onCues(cueGroup: CueGroup) {
                    onCues(cueGroup)
                }
            }
            playerListener = listener
            addListener(listener)

            if (snapshot != null) {
                seekTo(snapshot.videoPositionMs)
                playWhenReady = snapshot.videoIsPlaying
                playbackParameters = PlaybackParameters(snapshot.videoPlaybackSpeed)
                volume = snapshot.videoVolume
            } else {
                playWhenReady = true
            }

            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            setMediaItem(mediaItem)
            prepare()
        }
        runtime.setVideoSurfaceEnabled(true)
        // S0964: seed the panel model from the real player state (snapshot-aware) so the HUD does
        // not show stale defaults (volume 1.0 / playing) on warm entries from the flat player.
        onPlayerChanged(player)
        Timber.d("Started video playback for: ${file.name}")
        return true
    }

    fun release() {
        runtime.setVideoSurfaceEnabled(false)
        player?.clearVideoSurface()
        player?.stop()
        playerListener?.let { player?.removeListener(it) }
        player?.release()
        player = null
        playerListener = null
        // S0986: the host mirrors null into HudPlaybackController and drops any stale cue so the
        // subtitle quad hides when playback stops or media switches.
        onPlayerChanged(null)
    }
}
