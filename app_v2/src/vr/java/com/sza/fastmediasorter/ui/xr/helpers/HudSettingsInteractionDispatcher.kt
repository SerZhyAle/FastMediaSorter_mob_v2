package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.RectF

/**
 * S1271: maps ray UV events onto the settings panel's rows while [HudSettingsRenderer] owns the
 * HUD quad.
 *
 * A sibling of [HudInteractionDispatcher], not an extension of it: both map one UV space onto one
 * renderer's rectangles, and the host selects exactly one of them by the current quad owner - a
 * hidden strip's rectangles must never receive a settings-panel ray click (strategic §4).
 */
class HudSettingsInteractionDispatcher(
    private val renderer: HudSettingsRenderer,
    private val listener: SettingsListener
) {

    /** Dedicated settings-row callbacks; the host applies values to the live session (Phase 04). */
    interface SettingsListener {
        fun onLayoutCycle(step: Int)
        fun onProjectionCycle(step: Int)
        fun onDistanceChanged(value: Float)
        fun onSizeChanged(value: Float)
        fun onSubtitlesCycle(step: Int)
        fun onResumeCycle(step: Int)
        fun onHoverStateChanged(isHovered: Boolean)
    }

    private var wasHovered = false
    private var isTriggerPressed = false

    fun dispatch(uvX: Float, uvY: Float, isHover: Boolean, isClick: Boolean) {
        if (isHover != wasHovered) {
            listener.onHoverStateChanged(isHover)
            wasHovered = isHover
        }
        renderer.hasHover = isHover
        if (!isHover) {
            renderer.hoverX = -1f
            renderer.hoverY = -1f
            isTriggerPressed = false
            return
        }

        val px = uvX * HudSettingsRenderer.WIDTH
        // Ray UV.y is 0 at the quad BOTTOM (GL convention) while renderer rects are Canvas-space
        // top-down - same flip as the strip dispatcher (S1228 lesson).
        val py = (1f - uvY) * HudSettingsRenderer.HEIGHT
        renderer.hoverX = px
        renderer.hoverY = py

        val clickTriggered = isClick && !isTriggerPressed
        isTriggerPressed = isClick

        if (clickTriggered) dispatchArrowClick(px, py)
        if (isClick) dispatchSliderDrag(px, py)
    }

    private fun dispatchArrowClick(px: Float, py: Float) {
        when {
            renderer.layoutPrevRect.contains(px, py) -> listener.onLayoutCycle(-1)
            renderer.layoutNextRect.contains(px, py) -> listener.onLayoutCycle(1)
            renderer.projectionPrevRect.contains(px, py) -> listener.onProjectionCycle(-1)
            renderer.projectionNextRect.contains(px, py) -> listener.onProjectionCycle(1)
            renderer.subtitlesPrevRect.contains(px, py) -> listener.onSubtitlesCycle(-1)
            renderer.subtitlesNextRect.contains(px, py) -> listener.onSubtitlesCycle(1)
            renderer.resumePrevRect.contains(px, py) -> listener.onResumeCycle(-1)
            renderer.resumeNextRect.contains(px, py) -> listener.onResumeCycle(1)
        }
    }

    private fun dispatchSliderDrag(px: Float, py: Float) {
        if (isNearTrack(px, py, renderer.distanceTrackRect)) {
            val value = fractionOf(px, renderer.distanceTrackRect)
            renderer.distanceValue = value
            listener.onDistanceChanged(value)
        } else if (isNearTrack(px, py, renderer.sizeTrackRect)) {
            val value = fractionOf(px, renderer.sizeTrackRect)
            renderer.sizeValue = value
            listener.onSizeChanged(value)
        }
    }

    private fun fractionOf(px: Float, track: RectF): Float =
        ((px - track.left) / track.width()).coerceIn(0f, 1f)

    private fun isNearTrack(x: Float, y: Float, rect: RectF): Boolean =
        x >= rect.left && x <= rect.right &&
            y >= (rect.top - SLIDER_TOUCH_EXPAND_Y) && y <= (rect.bottom + SLIDER_TOUCH_EXPAND_Y)

    private companion object {
        // Same ergonomic vertical expansion the strip's sliders use.
        const val SLIDER_TOUCH_EXPAND_Y = 30f
    }
}
