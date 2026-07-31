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

        // S1232: the two terminal actions. Hide takes the whole strip away (playback continues,
        // the trigger summons it back); exit leaves the immersive session. No-op defaults keep the
        // banner-only diagnostic call site unaffected.
        fun onHideClick() {}
        fun onExitClick() {}

        // S1223: reopens the one-time controls legend. Shares the header band with the two terminal
        // actions but ends nothing, so it is a plain no-op default like them.
        fun onHelpClick() {}

        // S1239: a scrub previews continuously and seeks exactly once, on release (owner,
        // 2026-07-28). Seeking per ray sample would re-buffer a 44 GB network file dozens of times
        // across one drag.
        fun onSeekPreview(fraction: Float) {}
        fun onSeekCommit(fraction: Float) {}
    }

    private var wasHovered = false
    private var isTriggerPressed = false

    // S1239: while this is set the band owns the ray - the transport buttons and the other sliders
    // are not consulted, and the host's position tick stands down so it cannot yank the handle
    // back to the player's position mid-drag.
    private var isScrubbing = false
    private var scrubFraction = 0f

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
            // S1239: the ray leaving the quad mid-drag ends the scrub the way releasing the
            // trigger does. Cancelling instead would leave the handle where the user last saw it
            // while the film played on somewhere else.
            commitScrubIfActive()
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

        val wasPressed = isTriggerPressed
        isTriggerPressed = isClick

        if (!isClick && wasPressed) commitScrubIfActive()
        if (isScrubbing) {
            if (isClick) updateScrub(px)
            return
        }

        dispatchIdle(px, py, isClick = isClick, clickTriggered = isClick && !wasPressed)
    }

    /** Split out of [dispatch] to keep both bodies under the detekt complexity threshold. */
    private fun dispatchIdle(px: Float, py: Float, isClick: Boolean, clickTriggered: Boolean) {
        if (clickTriggered && dispatchTerminalClick(px, py)) return
        if (clickTriggered && beginScrubIfHit(px, py)) return
        if (clickTriggered) dispatchButtonClick(px, py)
        if (isClick) dispatchSliderDrag(px, py)
    }

    /** S1239: a press anywhere in the band jumps the handle there and starts the drag. */
    private fun beginScrubIfHit(px: Float, py: Float): Boolean {
        if (!renderer.seekRowVisible || !isPointInSeekBand(px, py)) return false
        isScrubbing = true
        updateScrub(px)
        return true
    }

    private fun updateScrub(px: Float) {
        val track = renderer.seekTrackRect
        val fraction = clamp((px - track.left) / track.width(), 0f, 1f)
        scrubFraction = fraction
        renderer.seekProgress = fraction
        listener.onSeekPreview(fraction)
    }

    private fun commitScrubIfActive() {
        if (!isScrubbing) return
        isScrubbing = false
        listener.onSeekCommit(scrubFraction)
    }

    /**
     * S1239: expanded up but barely down, unlike [isPointNearTrack]. The sliders can afford a
     * symmetric margin because nothing shares their columns; the seek band sits directly above the
     * transport trio and overlaps it horizontally, so reaching down as far as ROW_TOP would let a
     * single ray press PLAY and start a scrub at once. Upwards there is only empty header gap.
     */
    private fun isPointInSeekBand(x: Float, y: Float): Boolean {
        val track = renderer.seekTrackRect
        return x >= track.left && x <= track.right &&
            y >= (track.top - SEEK_TOUCH_EXPAND_UP) && y <= (track.bottom + SEEK_TOUCH_EXPAND_DOWN)
    }

    /** Sliders accept both a click on the track and a drag along it. */
    private fun dispatchSliderDrag(px: Float, py: Float) {
        if (renderer.volumeTrackRect.contains(px, py) || isPointNearTrack(px, py, renderer.volumeTrackRect)) {
            val vol = clamp((px - renderer.volumeTrackRect.left) / renderer.volumeTrackRect.width(), 0f, 1f)
            renderer.volume = vol
            listener.onVolumeChanged(vol)
        } else if (renderer.depthRowVisible &&
            (renderer.depthTrackRect.contains(px, py) || isPointNearTrack(px, py, renderer.depthTrackRect))
        ) {
            // S1238: the depth slider exists only for stereo content; a hidden slider must not react.
            val dep = clamp((px - renderer.depthTrackRect.left) / renderer.depthTrackRect.width(), 0f, 1f)
            renderer.depth = dep
            listener.onDepthChanged(dep)
        }
    }

    /**
     * S1232: hide and exit. Checked before the ordinary controls and reported as consumed, so a
     * terminal press can never also fall through to a transport button underneath it.
     *
     * S1223: help joined them. All three live in the header band and share that precedence; only
     * two of them are terminal, which is why the name undersells what this now dispatches.
     */
    private fun dispatchTerminalClick(px: Float, py: Float): Boolean {
        return when {
            renderer.hideRect.contains(px, py) -> {
                listener.onHideClick()
                true
            }
            renderer.helpRect.contains(px, py) -> {
                listener.onHelpClick()
                true
            }
            renderer.exitRect.contains(px, py) -> {
                listener.onExitClick()
                true
            }
            else -> false
        }
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

        // S1239: 140 - 26 = 114 clears the header band's buttons only horizontally, which is
        // enough because they sit at the far ends; 164 + 10 = 174 stops short of ROW_TOP (176).
        const val SEEK_TOUCH_EXPAND_UP = 26f
        const val SEEK_TOUCH_EXPAND_DOWN = 10f
    }
}
