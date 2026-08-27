package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.PlayerSettings
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Applies the session's playback settings to ExoPlayer, and owns the playback-speed dialog.
 *
 * S1618: the settings dialog this class used to open was unreachable from the player and has been
 * removed - the playback-control dialog ([com.sza.fastmediasorter.ui.player.PlaybackControlDialogFragment])
 * owns every one of those choices, with a wider set of 3D modes. What is left here is the applying
 * half: the per-file reset that runs when playback becomes ready.
 */
class PlayerSettingsManager(
    private val activity: Activity,
    private val videoPlayerManagerProvider: () -> VideoPlayerManager,
    private val settingsRepository: SettingsRepository,
) {
    
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + scopeJob)
    
    /**
     * S1618: the session defaults, with nothing left in the UI that edits them.
     *
     * Kept rather than inlined because it is the shape [applyPlayerSettings] resets the player to on
     * every new file - the playback-control dialog then layers the user's live choices on top.
     */
    private val playerSettings = PlayerSettings()

    /**
     * Apply player settings to ExoPlayer.
     * Called when a new video starts.
     */
    fun applyPlayerSettings() {
        val appLanguage = com.sza.fastmediasorter.core.util.LocaleHelper.getLanguage(activity)
        videoPlayerManagerProvider().applyPlayerSettings(playerSettings, appLanguage)
        
        // S1641: unguarded on purpose. The style belongs to the SubtitleView, not to the act of
        // turning a track on, and it is inert while no cues render - so applying it on every
        // playback-ready covers all three paths that enable subtitles here (remembered channel
        // preference, the playback-control dialog's subtitle tab, PlayerDialogHelper), none of
        // which routes through playerSettings.
        applySubtitleStyling()
    }
    
    /**
     * Apply subtitle font styling from app settings (font size and family).
     */
    private fun applySubtitleStyling() {
        scope.launch {
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
                videoPlayerManagerProvider().applySubtitleStyle(fontSize, fontFamily)
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to apply subtitle styling")
            }
        }
    }
    
    /**
     * Show playback speed selection dialog.
     * Displays speed options from 0.25x to 2.0x.
     */
    fun showPlaybackSpeedDialog() {
        val speeds = arrayOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val currentSpeed = videoPlayerManagerProvider().getPlayer()?.playbackParameters?.speed ?: 1.0f
        val currentIndex = speeds.indexOfFirst { 
            it.removeSuffix("x").toFloatOrNull() == currentSpeed 
        }.coerceAtLeast(3) // Default to 1.0x if not found
        
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.playback_speed))
            .setSingleChoiceItems(speeds, currentIndex) { dialog, which ->
                val speed = speeds[which].removeSuffix("x").toFloat()
                videoPlayerManagerProvider().setPlaybackSpeed(speed)
                dialog.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .showBoundToHost(activity)
    }
    
    /**
     * Release coroutine scope and cancel pending operations.
     * Call from PlayerLifecycleManager.onDestroy() to prevent coroutines touching stale Activity references (ML-005)
     */
    fun release() {
        scopeJob.cancel()
    }
}
