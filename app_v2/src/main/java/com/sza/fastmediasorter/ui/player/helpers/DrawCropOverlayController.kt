package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.views.CropOverlayView

/**
 * Mounts and unmounts the draggable crop-selection overlay over the draw-editor image (S0679).
 *
 * Reuses `player_crop_overlay_content.xml` (the player file-crop overlay): a [CropOverlayView] plus
 * confirm / cancel buttons. No business logic - it manages the overlay lifecycle and reports the
 * selection back through the [show] callbacks. While shown, the inflated root consumes touches so
 * the draw canvas underneath is suppressed.
 */
class DrawCropOverlayController(
    private val activity: Activity,
    private val imageContainer: ViewGroup
) {

    private var overlayRoot: View? = null

    val isShown: Boolean
        get() = overlayRoot != null

    fun show(
        onConfirm: (normalizedRect: RectF, viewW: Int, viewH: Int) -> Unit,
        onCancel: () -> Unit
    ) {
        if (isShown) return
        val root = LayoutInflater.from(activity)
            .inflate(R.layout.player_crop_overlay_content, imageContainer, false)
        // Consume taps that fall outside the handles/buttons so they never reach the draw canvas.
        root.isClickable = true
        imageContainer.addView(root)
        overlayRoot = root

        val cropView = root.findViewById<CropOverlayView>(R.id.crop_overlay_view)
        root.findViewById<View>(R.id.btn_crop_confirm).setOnClickListener {
            val rect = cropView.getCropRectNormalized()
            val w = cropView.width
            val h = cropView.height
            hide()
            onConfirm(rect, w, h)
        }
        root.findViewById<View>(R.id.btn_crop_cancel).setOnClickListener {
            hide()
            onCancel()
        }
        cropView.requestFocus()
    }

    fun hide() {
        overlayRoot?.let { imageContainer.removeView(it) }
        overlayRoot = null
    }
}
