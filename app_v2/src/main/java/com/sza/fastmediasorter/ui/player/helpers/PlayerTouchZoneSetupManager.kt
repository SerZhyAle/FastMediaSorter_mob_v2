package com.sza.fastmediasorter.ui.player.helpers

import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import timber.log.Timber

/**
 * Sets up deprecated view-based touch zone listeners and hint overlays.
 *
 * Responsibilities:
 * - Wire legacy 2-zone and 3-zone click listeners (permanently hidden views, preserved for
 *   backward compatibility - actual navigation is handled by TouchZoneGestureManager)
 * - Show context-aware hint overlays (9-zone grid, 3-zone text, media-bottom-reserved)
 * - Dismiss overlays on first tap
 *
 * The view-based overlays (touchZonesOverlay, touchZones3Overlay) are always hidden.
 * All real touch zone detection uses TouchZoneGestureManager gesture callbacks.
 */
class PlayerTouchZoneSetupManager(
    private val activity: PlayerActivity
) {
    private val safeViews get() = activity.safeViews
    private val navigationManager get() = activity.navigationManager

    /**
     * Wire legacy 2-zone and 3-zone click listeners.
     * Views are always hidden - listeners preserved for backward compatibility only.
     */
    fun setupLegacyTouchZoneListeners() {
        safeViews.touchZonePrevious.setOnClickListener {
            navigationManager.navigatePreviousFromTouchZone()
        }
        safeViews.touchZoneNext.setOnClickListener {
            navigationManager.navigateNextFromTouchZone()
        }
        safeViews.touchZone3Previous.setOnClickListener {
            navigationManager.navigatePreviousFromTouchZone()
        }
        safeViews.touchZone3Next.setOnClickListener {
            navigationManager.navigateNextFromTouchZone()
        }
        // Center gesture zone - no click handler, handled by TouchZoneGestureManager
    }

    /**
     * Show context-aware touch zone hint overlay based on current mode.
     * - FULLSCREEN_9ZONE: 9-zone grid overlay (audioTouchZonesOverlay)
     * - COMMAND_PANEL_3ZONE: text overlay describing 3-zone layout
     * - MEDIA_BOTTOM_RESERVED: text overlay describing reserved bottom area
     * Dismissed on first tap.
     */
    fun showHintOverlay(type: TouchZoneHintType) {
        Timber.d("PlayerTouchZoneSetupManager: showHintOverlay($type)")
        when (type) {
            TouchZoneHintType.FULLSCREEN_9ZONE -> {
                safeViews.audioTouchZonesOverlay.isVisible = true
                safeViews.audioTouchZonesOverlay.alpha = 1.0f
                safeViews.audioTouchZonesOverlay.setOnClickListener {
                    safeViews.audioTouchZonesOverlay.isVisible = false
                    safeViews.audioTouchZonesOverlay.setOnClickListener(null)
                }
            }
            TouchZoneHintType.COMMAND_PANEL_3ZONE -> {
                safeViews.tvFirstRunHintText.setText(R.string.hint_touch_zone_3zone)
                safeViews.firstRunHintOverlay.isVisible = true
                safeViews.firstRunHintOverlay.setOnClickListener {
                    safeViews.firstRunHintOverlay.isVisible = false
                    safeViews.firstRunHintOverlay.setOnClickListener(null)
                }
            }
            TouchZoneHintType.MEDIA_BOTTOM_RESERVED -> {
                safeViews.tvFirstRunHintText.setText(R.string.hint_touch_zone_media)
                safeViews.firstRunHintOverlay.isVisible = true
                safeViews.firstRunHintOverlay.setOnClickListener {
                    safeViews.firstRunHintOverlay.isVisible = false
                    safeViews.firstRunHintOverlay.setOnClickListener(null)
                }
            }
        }
    }
}
