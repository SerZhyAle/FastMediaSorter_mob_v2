package com.sza.fastmediasorter.vr.ui

import com.sza.fastmediasorter.vr.render.ActionBadge
import com.sza.fastmediasorter.vr.render.RepeatMode

/**
 * Backend-agnostic API for HUD indicators in the VR player.
 *
 * Two backends implement this:
 *  - [VrHudIndicatorManager] — Android view overlay attached to decorView.
 *    Active when no XR session is running (phone fallback).
 *  - [com.sza.fastmediasorter.vr.render.VrHudSceneDriver] — OpenXR composition
 *    layer driven from a Canvas bitmap. Active inside an immersive XR session.
 *
 * The active backend is swapped by [com.sza.fastmediasorter.vr.VrPlayerActivity]
 * on session ready / stopped. Callers see one stable API.
 */
interface VrHudSink {
    fun showPauseIndicator(paused: Boolean)
    fun showVolumeIndicator(percent: Int)
    fun showSeekIndicator(deltaSeconds: Int, positionMs: Long, totalMs: Long)
    fun showFileIndicator(name: String, index: Int, total: Int)
    fun showZoomIndicator(factor: Float)
    fun showRecenterFlash()
    fun showBoundaryMessage(isFirst: Boolean)
    fun showErrorMessage(message: String)
    fun showImmersiveModeChanged(immersive: Boolean)
    fun showActionBadge(badge: ActionBadge)
    fun showRepeatMode(mode: RepeatMode?)
    fun showBannerText(text: String?)
    /**
     * Notify the HUD that the user has interacted with the controller or joystick.
     * Keeps the HUD visible for [VrHudSceneDriver.IDLE_HIDE_DELAY_MS] (15 s) and then
     * auto-hides when no further activity is reported.
     */
    fun reportActivity()
    /** Update the stereo-mode badge shown at top-left (e.g. "2D", "360° SBS"). Null = clear. */
    fun showStereoModeLabel(label: String?)
    fun updateProgress(positionMs: Long, bufferedMs: Long, totalMs: Long)
    fun hideAll()
}
