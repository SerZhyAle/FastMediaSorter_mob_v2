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

        // Convert UV to Canvas pixels
        // OpenXR UV coordinates have V=0 at the top and V=1 at the bottom.
        // UV.x -> Canvas width pixels
        // UV.y -> Canvas height pixels (both GLES and Canvas match: top is 0, bottom is 512).
        val px = uvX * HudCanvasRenderer.WIDTH
        val py = uvY * HudCanvasRenderer.HEIGHT

        renderer.hoverX = px
        renderer.hoverY = py

        val clickTriggered = isClick && !isTriggerPressed
        isTriggerPressed = isClick

        if (clickTriggered) {
            // Check buttons
            if (renderer.prevRect.contains(px, py)) {
                listener.onPrevClick()
            } else if (renderer.playPauseRect.contains(px, py)) {
                listener.onPlayPauseClick()
            } else if (renderer.nextRect.contains(px, py)) {
                listener.onNextClick()
            }
        }

        // Check sliders (handle dragging or clicking on slider tracks)
        if (isClick) {
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
    }

    private fun isPointNearTrack(x: Float, y: Float, rect: RectF): Boolean {
        // Expand vertical click target for sliders to be more ergonomic in VR
        val expandY = 30f
        return x >= rect.left && x <= rect.right && y >= (rect.top - expandY) && y <= (rect.bottom + expandY)
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return Math.max(min, Math.min(max, value))
    }
}
