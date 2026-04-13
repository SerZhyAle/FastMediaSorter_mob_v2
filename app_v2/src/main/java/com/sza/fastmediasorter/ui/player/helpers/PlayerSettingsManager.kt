package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.PlayerSettingsDialog
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages player settings dialog and ExoPlayer settings application.
 * 
 * Handles:
 * - Player settings dialog display
 * - Playback speed selection
 * - Settings application to ExoPlayer (via VideoPlayerManager)
 * - Settings persistence for session
 */
class PlayerSettingsManager(
    private val activity: Activity,
    private val dialogHelper: com.sza.fastmediasorter.ui.player.PlayerDialogHelper,
    private val videoPlayerManagerProvider: () -> VideoPlayerManager,
    private val settingsRepository: SettingsRepository,
    private val callback: Callback
) {
    
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + scopeJob)
    
    /**
     * Player settings (persist for session).
     */
    var playerSettings = PlayerSettingsDialog.PlayerSettings()
        private set
    
    /**
     * Show player settings dialog for video/audio files.
     * Displays options like playback speed, video quality, subtitles, etc.
     */
    fun showPlayerSettingsDialog() {
        dialogHelper.showPlayerSettingsDialog(playerSettings) { newSettings ->
            playerSettings = newSettings
            applyPlayerSettings()
        }
    }
    
    /**
     * Apply player settings to ExoPlayer.
     * Called after settings dialog is closed or when new video starts.
     */
    fun applyPlayerSettings() {
        val appLanguage = com.sza.fastmediasorter.core.util.LocaleHelper.getLanguage(activity)
        videoPlayerManagerProvider().applyPlayerSettings(playerSettings, appLanguage)
        
        // Apply subtitle styling from saved font settings when subtitles are enabled
        if (playerSettings.showSubtitles) {
            applySubtitleStyling()
        }
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
                    TranslationFontSize.AUTO
                }
                val fontFamily = try {
                    TranslationFontFamily.valueOf(settings.ocrDefaultFontFamily)
                } catch (e: Exception) {
                    TranslationFontFamily.DEFAULT
                }
                videoPlayerManagerProvider().applySubtitleStyle(fontSize, fontFamily)
            } catch (e: Exception) {
                Timber.e(e, "Failed to apply subtitle styling")
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
            .show()
    }
    
    /**
     * Release coroutine scope and cancel pending operations.
     * Call from PlayerLifecycleManager.onDestroy() to prevent coroutines touching stale Activity references (ML-005)
     */
    fun release() {
        scopeJob.cancel()
    }

    /**
     * Callback interface for PlayerSettingsManager events.
     */
    interface Callback {
        // Currently no callbacks needed - may add in future for settings changes
    }
}
