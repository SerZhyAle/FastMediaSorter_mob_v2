package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages playback speed, audio track, and subtitle track dialogs for
 * StandalonePlayerActivity. Delegates track operations to VideoTrackSelectionManager.
 */
class StandalonePlayerSettingsManager(
    private val activity: Activity,
    private val playerView: PlayerView,
    private val settingsRepository: SettingsRepository,
    private val trackSelectionManager: VideoTrackSelectionManager,
    private val lifecycleScope: CoroutineScope
) {

    fun showPlaybackSpeedDialog() {
        val speeds = arrayOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val currentSpeed = playerView.player?.playbackParameters?.speed ?: 1.0f
        val currentIndex = speeds.indexOfFirst {
            it.removeSuffix("x").toFloatOrNull() == currentSpeed
        }.coerceAtLeast(3)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.playback_speed))
            .setSingleChoiceItems(speeds, currentIndex) { dialog, which ->
                val speed = speeds[which].removeSuffix("x").toFloat()
                playerView.player?.setPlaybackSpeed(speed)
                Timber.d("StandalonePlayerSettingsManager: speed set to ${speed}x")
                dialog.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .showBoundToHost(activity)
    }

    fun showAudioTrackDialog() {
        val tracks = trackSelectionManager.getAvailableAudioTracks()
        if (tracks.isEmpty()) return

        val labels = tracks.map { it.label }.toTypedArray()
        val currentIndex = tracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.select_audio_track))
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val track = tracks[which]
                trackSelectionManager.selectAudioTrack(track.groupIndex, track.trackIndex)
                Timber.d("StandalonePlayerSettingsManager: audio track selected group=${track.groupIndex} track=${track.trackIndex}")
                dialog.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .showBoundToHost(activity)
    }

    fun showSubtitleTrackDialog() {
        val tracks = trackSelectionManager.getAvailableSubtitleTracks()
        if (tracks.isEmpty()) return

        val offLabel = activity.getString(R.string.subtitle_off)
        val labels = (listOf(offLabel) + tracks.map { it.label }).toTypedArray()
        val selectedIndex = tracks.indexOfFirst { it.isSelected }
            .let { if (it < 0) 0 else it + 1 }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.select_subtitle_track))
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                if (which == 0) {
                    trackSelectionManager.selectSubtitleTrack(-1, -1)
                    Timber.d("StandalonePlayerSettingsManager: subtitles disabled")
                } else {
                    val track = tracks[which - 1]
                    trackSelectionManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                    applySubtitleStyling()
                    Timber.d("StandalonePlayerSettingsManager: subtitle track selected group=${track.groupIndex} track=${track.trackIndex}")
                }
                dialog.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .showBoundToHost(activity)
    }

    private fun applySubtitleStyling() {
        lifecycleScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                val fontSize = try {
                    TranslationFontSize.valueOf(settings.ocrDefaultFontSize)
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    TranslationFontSize.AUTO
                }
                val fontFamily = try {
                    TranslationFontFamily.valueOf(settings.ocrDefaultFontFamily)
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    TranslationFontFamily.DEFAULT
                }
                trackSelectionManager.applySubtitleStyle(fontSize, fontFamily)
            } catch (e: Exception) {
                e.errorUnlessCancellation("StandalonePlayerSettingsManager: failed to apply subtitle styling")
            }
        }
    }
}
