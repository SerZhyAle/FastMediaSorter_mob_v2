package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.ui.player.helpers.PanelStereoCropApplier
import com.sza.fastmediasorter.ui.player.helpers.applyConfiguredVideoEffects
import com.sza.fastmediasorter.ui.player.helpers.brightnessAdjustmentToProgress
import com.sza.fastmediasorter.ui.player.helpers.brightnessProgressToAdjustment
import com.sza.fastmediasorter.ui.player.helpers.saveCurrentPosition
import timber.log.Timber

/** Groups user-driven control methods that only mutate already-initialized playback state. */
internal class VideoPlaybackControlsHelper(
    private val manager: VideoPlayerManager,
    private val context: Context,
    private val playbackControlPrefs: SharedPreferences,
    private val trackSelectionManager: VideoTrackSelectionManager
) {
    fun applyStereoEffect(mode: StereoMode) {
        val resolved = when (mode) {
            StereoMode.AUTO, StereoMode.UNKNOWN -> {
                Timber.w(
                    "VideoPlayerManager: applyStereoEffect sentinel input=%s - defensive fallback to MONO " +
                        "(upstream coordinator should have suppressed; see PlayerStereoModeCoordinator)",
                    mode,
                )
                StereoMode.MONO
            }

            else -> mode
        }
        Timber.d("VideoPlayerManager: applyStereoEffect requested=$mode resolved=$resolved")
        val singleEyeEnabled = manager.panelStereoSingleEyeEnabled && !manager.vrImmersiveActive
        val effectiveMode = if (singleEyeEnabled) {
            resolved
        } else {
            StereoMode.MONO
        }
        manager.stereoVideoProcessor.setStereoMode(effectiveMode)
        manager.applyConfiguredVideoEffects()

        // S0264: Media3 1.2.1 effects pipeline does not visually render Crop when PlayerView
        // uses surface_type=texture_view (issue androidx/media#779). Apply the single-eye
        // crop directly to the TextureView matrix instead - this is what actually moves pixels.
        PanelStereoCropApplier.apply(
            playerView = manager.currentPlayerView,
            mode = resolved,
            singleEyeEnabled = singleEyeEnabled,
        )

        if (effectiveMode != StereoMode.MONO) {
            manager.panelStereoSingleEyeNotifier.notifyIfFirstThisSession(context)
        }
    }

    fun setHueAdjustmentDegrees(hueDegrees: Float) {
        manager.videoColorProcessor.setHueAdjustmentDegrees(hueDegrees)
        Timber.d(
            "VideoPlayerManager: setHueAdjustmentDegrees requested=$hueDegrees " +
                "stored=${manager.videoColorProcessor.getHueAdjustmentDegrees()} pipelineActive=${manager.effectsPipelineActive}"
        )
        playbackControlPrefs.edit()
            .putFloat(
                PlaybackControlPreferences.KEY_HUE_DEGREES,
                manager.videoColorProcessor.getHueAdjustmentDegrees()
            )
            .apply()
        manager.applyConfiguredVideoEffects()
    }

    fun getHueAdjustmentDegrees(): Float = manager.videoColorProcessor.getHueAdjustmentDegrees()

    fun setBrightnessAdjustment(brightnessAdjustment: Float) {
        manager.videoColorProcessor.setBrightnessAdjustment(brightnessAdjustment)
        Timber.d(
            "VideoPlayerManager: setBrightnessAdjustment requested=$brightnessAdjustment " +
                "stored=${manager.videoColorProcessor.getBrightnessAdjustment()} pipelineActive=${manager.effectsPipelineActive}"
        )
        playbackControlPrefs.edit()
            .putInt(
                PlaybackControlPreferences.KEY_BRIGHTNESS_PERCENT,
                manager.brightnessAdjustmentToProgress(manager.videoColorProcessor.getBrightnessAdjustment())
            )
            .apply()
        manager.applyConfiguredVideoEffects()
    }

    fun getBrightnessAdjustment(): Float = manager.videoColorProcessor.getBrightnessAdjustment()

    fun setBrightnessProgress(progress: Int) {
        setBrightnessAdjustment(manager.brightnessProgressToAdjustment(progress))
    }

    fun getBrightnessProgress(): Int =
        manager.brightnessAdjustmentToProgress(manager.videoColorProcessor.getBrightnessAdjustment())

    fun getBrightnessPercentOffset(): Int =
        ((getBrightnessProgress() - VideoPlayerManager.DEFAULT_BRIGHTNESS_PROGRESS) * 100f /
            VideoPlayerManager.DEFAULT_BRIGHTNESS_PROGRESS).toInt()

    fun setRepeatMode(repeatMode: Int) {
        manager.exoPlayer?.repeatMode = repeatMode
    }

    fun pause() {
        manager.saveCurrentPosition()
        if (manager.isUsingMediaPlayer) {
            if (manager.mediaPlayer?.isPlaying == true) manager.mediaPlayer?.pause()
        } else {
            manager.exoPlayer?.pause()
        }
    }

    fun play() {
        if (manager.isUsingMediaPlayer) manager.mediaPlayer?.start() else manager.exoPlayer?.play()
    }

    fun setPlaybackSpeed(speed: Float) {
        Timber.d("VideoPlayerManager: setPlaybackSpeed ${speed}x")
        manager.exoPlayer?.setPlaybackSpeed(speed)
        playbackControlPrefs.edit().putFloat(PlaybackControlPreferences.KEY_SPEED, speed).apply()
    }

    fun applyPlayerSettings(settings: PlayerSettings, appLanguage: String) {
        val player = manager.exoPlayer ?: return
        val savedSpeed = playbackControlPrefs.getFloat(PlaybackControlPreferences.KEY_SPEED, -1f)
        val speedToApply = if (savedSpeed > 0f) savedSpeed else settings.playbackSpeed
        manager.exoPlayer?.setPlaybackSpeed(speedToApply)
        Timber.d(
            "VideoPlayerManager: Set playback speed to ${speedToApply}x " +
                "(saved=$savedSpeed, settings=${settings.playbackSpeed})"
        )
        setRepeatMode(if (settings.repeatVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF)
        trackSelectionManager.applyTrackSelection(player, settings, appLanguage)
    }

    fun applySubtitleStyle(fontSize: TranslationFontSize, fontFamily: TranslationFontFamily) =
        trackSelectionManager.applySubtitleStyle(fontSize, fontFamily)

    fun getAvailableAudioTracks(): List<VideoPlayerManager.TrackInfo> =
        trackSelectionManager.getAvailableAudioTracks()
            .map {
                VideoPlayerManager.TrackInfo(it.groupIndex, it.trackIndex, it.label, it.isSelected, it.language)
            }

    fun getAvailableSubtitleTracks(): List<VideoPlayerManager.TrackInfo> =
        trackSelectionManager.getAvailableSubtitleTracks()
            .map {
                VideoPlayerManager.TrackInfo(it.groupIndex, it.trackIndex, it.label, it.isSelected, it.language)
            }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) =
        trackSelectionManager.selectAudioTrack(groupIndex, trackIndex)

    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) =
        trackSelectionManager.selectSubtitleTrack(groupIndex, trackIndex)

    fun hasMultipleAudioTracks(): Boolean = trackSelectionManager.hasMultipleAudioTracks()

    fun hasSubtitleTracks(): Boolean = trackSelectionManager.hasSubtitleTracks()

    fun getAudioFormat(): VideoPlayerManager.AudioFormat? {
        val player = manager.exoPlayer ?: return null
        val audioTrack = player.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
            ?.getTrackFormat(0) ?: return null
        return VideoPlayerManager.AudioFormat(
            codec = audioTrack.sampleMimeType ?: "Unknown",
            sampleRate = audioTrack.sampleRate,
            channelCount = audioTrack.channelCount,
            bitrate = audioTrack.bitrate
        )
    }
}