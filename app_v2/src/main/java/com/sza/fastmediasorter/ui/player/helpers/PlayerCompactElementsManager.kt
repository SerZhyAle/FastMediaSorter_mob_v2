package com.sza.fastmediasorter.ui.player.helpers

import android.util.TypedValue
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding

/**
 * Manages compact-elements scaling for Player text overlays.
 * Driven by [AppSettings.useCompactElements] (global compact mode).
 *
 * Applies ONLY the text/overlay scaling NOT covered by [CommandPanelController.applySmallControlsIfNeeded]:
 * - tvFileNameOverlay
 * - tvBackgroundMusicTrack
 * - tvCountdown
 *
 * Guard: command-button heights are already handled by showSmallControls path;
 * this manager operates only on text sizes and panel text scaling.
 */
class PlayerCompactElementsManager(
    private val binding: ActivityPlayerUnifiedBinding
) {
    private val safeViews = PlayerBindingSafeViews(binding)

    fun apply() {
        binding.tvFileNameOverlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        safeViews.tvBackgroundMusicTrack.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        safeViews.tvCountdown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
    }

    fun restore() {
        binding.tvFileNameOverlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        safeViews.tvBackgroundMusicTrack.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        safeViews.tvCountdown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
    }
}
