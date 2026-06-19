package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Matrix
import android.view.TextureView
import android.view.View
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber
import java.util.WeakHashMap

/**
 * S0264 - applies a single-eye crop directly to the `TextureView` that backs `PlayerView`
 * when the underlying surface type is `texture_view`.
 *
 * Background. `ExoPlayer.setVideoEffects()` is not visually supported by Media3 1.2.1
 * when `PlayerView` uses `app:surface_type="texture_view"` - the effects pipeline only
 * outputs to a `SurfaceView`/`SurfaceHolder`. The `Crop` effect attached via
 * `StereoVideoProcessor.buildGlEffect` therefore lands in the player but never reaches
 * the on-screen pixels. See androidx/media issue #779 for the upstream confirmation.
 *
 * Workaround. We bypass the effects pipeline for the stereo crop and apply a `Matrix`
 * directly on the backing `TextureView`. The matrix scales the texture ×2 along the
 * stereo axis and translates so only one eye fills the viewport. PlayerView's own
 * letterbox / aspect handling still runs first; the matrix is applied on top.
 *
 * The matrix must be re-applied on every layout change (rotation, controller toggle)
 * because `TextureView.setTransform` is reset when the view is re-laid-out by
 * `PlayerView.applyTextureViewRotation`. The applier wires a layout listener on first
 * use and the listener stays alive for the lifetime of the TextureView.
 */
object PanelStereoCropApplier {

    /** Per-TextureView state used by the layout listener to re-apply the transform. */
    private data class State(var mode: StereoMode, var singleEyeEnabled: Boolean)

    /**
     * Keyed by [TextureView] rather than [PlayerView] because the TextureView lifecycle
     * outlives surface swaps inside the PlayerView. `WeakHashMap` keeps the entry alive
     * only while the view itself is reachable from the view hierarchy.
     */
    private val states: MutableMap<TextureView, State> = WeakHashMap()
    private val listenerAttached: MutableSet<TextureView> =
        java.util.Collections.newSetFromMap(WeakHashMap())

    /**
     * Apply the per-mode matrix transform to the `TextureView` backing [playerView].
     *
     * No-op if the view is not yet laid out, the video dimensions are unknown, or the
     * surface is not a `TextureView` (e.g. `surface_type=surface_view`).
     */
    fun apply(playerView: PlayerView?, mode: StereoMode, singleEyeEnabled: Boolean) {
        val view = playerView ?: return
        val tex = view.videoSurfaceView as? TextureView ?: return

        states[tex] = State(mode, singleEyeEnabled)
        attachListenerOnce(tex)

        if (view.width == 0 || view.height == 0) {
            // View is not laid out yet; the listener will fire as soon as it is.
            Timber.d(
                "PanelStereoCropApplier: deferred - viewport 0x0, layout listener will pick up mode=%s singleEye=%s",
                mode,
                singleEyeEnabled,
            )
            return
        }

        applyToView(tex, view.width, view.height, mode, singleEyeEnabled)
    }

    /**
     * Reset any previously-applied transform back to identity.
     * Called when stereo mode goes to MONO or when single-eye is toggled off.
     */
    fun reset(playerView: PlayerView?) {
        val view = playerView ?: return
        val tex = view.videoSurfaceView as? TextureView ?: return
        states[tex] = State(StereoMode.MONO, singleEyeEnabled = false)
        tex.setTransform(Matrix())
        tex.invalidate()
        Timber.d("PanelStereoCropApplier: reset transform")
    }

    private fun applyToView(
        tex: TextureView,
        width: Int,
        height: Int,
        mode: StereoMode,
        singleEyeEnabled: Boolean,
    ) {
        val matrix = if (singleEyeEnabled) buildMatrixFor(mode, width, height) else null
        tex.setTransform(matrix ?: Matrix())
        tex.invalidate()
        Timber.d(
            "PanelStereoCropApplier: applied transform mode=%s singleEye=%s viewport=%dx%d cropped=%s",
            mode,
            singleEyeEnabled,
            width,
            height,
            matrix != null,
        )
    }

    private fun buildMatrixFor(mode: StereoMode, w: Int, h: Int): Matrix? {
        if (w <= 0 || h <= 0) return null
        val m = Matrix()
        when (mode) {
            StereoMode.SBS_FULL,
            StereoMode.SBS_HALF,
            StereoMode.EQUIRECT_360_SBS,
            StereoMode.EQUIRECT_180_SBS,
            StereoMode.VR180_FISHEYE_SBS -> {
                // Right eye = right half of the SBS frame. Scale ×2 around the right-half
                // centre so the right half stretches to fill the whole TextureView.
                m.postScale(2f, 1f, w * 0.75f, h * 0.5f)
            }
            StereoMode.OU,
            StereoMode.EQUIRECT_360_OU -> {
                // Bottom eye = bottom half. Same logic on the Y axis.
                m.postScale(1f, 2f, w * 0.5f, h * 0.75f)
            }
            else -> return null
        }
        return m
    }

    /**
     * Install a layout listener on [tex] exactly once. The listener consults the
     * per-view state map on every layout pass and re-applies the matrix so the
     * transform survives rotations, controller-bar visibility changes, and any other
     * PlayerView re-layout that resets `setTransform`.
     */
    private fun attachListenerOnce(tex: TextureView) {
        if (!listenerAttached.add(tex)) return

        tex.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
            ) {
                val width = right - left
                val height = bottom - top
                if (width <= 0 || height <= 0) return
                val texView = v as? TextureView ?: return
                val state = states[texView] ?: return
                applyToView(texView, width, height, state.mode, state.singleEyeEnabled)
            }
        })
    }
}
