package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.RectF

class HudInteractionDispatcher(
    private val renderer: HudCanvasRenderer,
    private val listener: InteractionListener
) {

    interface InteractionListener {
        fun onPlayPauseClick()
        fun onNextClick()
        fun onPrevClick()
        fun onVolumeChanged(volume: Float)
        fun onDepthChanged(depth: Float)
        fun onHoverStateChanged(isHovered: Boolean)

        // S0964: track cycle rows; default no-ops so the diagnostic call site (banner-only HUD)
        // does not have to care about track rows.
        fun onAudioTrackCycle(step: Int) {}
        fun onSubtitleTrackCycle(step: Int) {}

        // S1228: close/restore changes what the panel paints, so the host must re-queue the
        // texture. No-op default keeps the banner-only diagnostic call site unaffected.
        fun onCollapseToggled(collapsed: Boolean) {}
    }

    private var wasHovered = false
    private var isTriggerPressed = false

    fun dispatch(uvX: Float, uvY: Float, isHover: Boolean, isClick: Boolean) {
        val hoverChanged = isHover != wasHovered
        if (hoverChanged) {
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

        val px = uvX * HudCanvasRenderer.WIDTH
        // S1228: ray UV.y is 0 at the quad BOTTOM and 1 at the TOP (GL convention, xr_raycast.cpp),
        // while the renderer's rects live in Canvas space with y=0 at the TOP. Without this flip the
        // hit point is mirrored vertically, so the owner had to aim well above the control being
        // pressed. The browser dispatcher already flips (S1132); this call site kept the opposite
        // assumption as a confident comment ("V=0 at the top"), which is why it survived review.
        val py = (1f - uvY) * HudCanvasRenderer.HEIGHT

        renderer.hoverX = px
        renderer.hoverY = py

        val clickTriggered = isClick && !isTriggerPressed
        isTriggerPressed = isClick

        val consumedByToggle = clickTriggered && dispatchCollapseToggle(px, py)
        if (!consumedByToggle && !renderer.isCollapsed) {
            if (clickTriggered) dispatchButtonClick(px, py)
            if (isClick) dispatchSliderDrag(px, py)
        }
    }

    /** Sliders accept both a click on the track and a drag along it. */
    private fun dispatchSliderDrag(px: Float, py: Float) {
        if (renderer.volumeTrackRect.contains(px, py) || isPointNearTrack(px, py, renderer.volumeTrackRect)) {
            val vol = clamp((px - renderer.volumeTrackRect.left) / renderer.volumeTrackRect.width(), 0f, 1f)
            renderer.volume = vol
            listener.onVolumeChanged(vol)
        } else if (renderer.depthTrackRect.contains(px, py) || isPointNearTrack(px, py, renderer.depthTrackRect)) {
            val dep = clamp((px - renderer.depthTrackRect.left) / renderer.depthTrackRect.width(), 0f, 1f)
            renderer.depth = dep
            listener.onDepthChanged(dep)
        }
    }

    /**
     * S1228: close/restore. Returns true when the click was consumed, so a collapsed panel never
     * dispatches to controls that are not painted.
     */
    private fun dispatchCollapseToggle(px: Float, py: Float): Boolean {
        val hitCollapsed = renderer.isCollapsed && renderer.expandRect.contains(px, py)
        val hitClose = !renderer.isCollapsed && renderer.closeRect.contains(px, py)
        if (!hitCollapsed && !hitClose) return false
        renderer.isCollapsed = !renderer.isCollapsed
        listener.onCollapseToggled(renderer.isCollapsed)
        return true
    }

    /** Extracted from [dispatch] to keep its cyclomatic complexity under the detekt threshold. */
    private fun dispatchButtonClick(px: Float, py: Float) {
        if (renderer.prevRect.contains(px, py)) {
            listener.onPrevClick()
        } else if (renderer.playPauseRect.contains(px, py)) {
            listener.onPlayPauseClick()
        } else if (renderer.nextRect.contains(px, py)) {
            listener.onNextClick()
        } else if (renderer.audioRowEnabled && renderer.audioPrevRect.contains(px, py)) {
            listener.onAudioTrackCycle(-1)
        } else if (renderer.audioRowEnabled && renderer.audioNextRect.contains(px, py)) {
            listener.onAudioTrackCycle(1)
        } else if (renderer.subsRowEnabled && renderer.subsPrevRect.contains(px, py)) {
            listener.onSubtitleTrackCycle(-1)
        } else if (renderer.subsRowEnabled && renderer.subsNextRect.contains(px, py)) {
            listener.onSubtitleTrackCycle(1)
        }
    }

    private fun isPointNearTrack(x: Float, y: Float, rect: RectF): Boolean {
        // Expand vertical click target for sliders to be more ergonomic in VR
        val expandY = SLIDER_TOUCH_EXPAND_Y
        return x >= rect.left && x <= rect.right && y >= (rect.top - expandY) && y <= (rect.bottom + expandY)
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return Math.max(min, Math.min(max, value))
    }

    private companion object {
        const val SLIDER_TOUCH_EXPAND_Y = 30f
    }
}
