package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.OutlinedTextView
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import java.util.Locale
import kotlin.math.abs

/**
 * S0844: renders the zoom-preset pills, live zoom slider/readout and active-lens label for
 * [com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity]. Extracted from the Activity to
 * shed its detekt `LargeClass`/`TooManyFunctions` findings - holds no zoom state of its own, every
 * live value it renders is supplied by the caller ([CameraCaptureFlowManager] stays the source of truth).
 */
class CameraZoomControlsManager(
    private val context: Context,
    private val presetGroup: ChipGroup,
    private val zoomSlider: Slider,
    private val zoomValue: OutlinedTextView,
    private val lensLabel: OutlinedTextView,
    private val onPresetSelected: (Float) -> Unit,
) {

    /** Builds one pill per zoom preset and selects the one nearest the live ratio. */
    fun configure(capabilities: CameraRuntimeCapabilities, liveZoomRatio: Float, liveLinearZoom: Float) {
        presetGroup.removeAllViews()
        val density = context.resources.displayMetrics.density
        val padH = (density * CHIP_SIDE_PADDING_DP).toInt()
        val padV = (density * CHIP_VERT_PADDING_DP).toInt()
        val spacing = (density * CHIP_SPACING_DP).toInt()
        capabilities.zoomPresets.forEach { preset ->
            // Custom see-through pill (the Material chip kept drawing an opaque light surface). The label
            // is the equivalent zoom (native ratio x lens multiplier), so an ultra-wide reads 0.5x.
            val pill = OutlinedTextView(context).apply {
                text = formatZoomLabel(preset * capabilities.zoomMultiplier)
                tag = preset
                contentDescription = context.getString(R.string.camera_control_zoom)
                isClickable = true
                isFocusable = true
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, CHIP_TEXT_SP)
                setPadding(padH, padV, padH, padV)
                background = ContextCompat.getDrawable(context, R.drawable.bg_camera_zoom_chip)
                setOnClickListener { onPresetSelected(preset) }
            }
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = spacing }
            presetGroup.addView(pill, lp)
        }
        syncSelection(liveZoomRatio, liveLinearZoom, capabilities.zoomMultiplier)
    }

    /** Highlights the preset pill nearest the live zoom ratio; clears all when pinched between steps. */
    fun syncSelection(liveZoomRatio: Float, liveLinearZoom: Float, zoomMultiplier: Float) {
        var best: View? = null
        var bestDelta = Float.MAX_VALUE
        presetGroup.children.forEach { view ->
            view.isSelected = false
            val preset = view.tag as? Float ?: return@forEach
            val delta = abs(preset - liveZoomRatio)
            if (delta < bestDelta) {
                bestDelta = delta
                best = view
            }
        }
        if (bestDelta < ZOOM_PILL_MATCH_EPSILON) best?.isSelected = true
        // Mirror the live linear position onto the slider (fromUser=false, so the listener no-ops).
        zoomSlider.value = liveLinearZoom.coerceIn(0f, 1f)
        // S0753: live equivalent-zoom readout next to the slider.
        zoomValue.text = formatZoomRatio(liveZoomRatio * zoomMultiplier)
    }

    /** Active-lens name next to the switch button (Ultra-wide / Wide / Tele / Front), by lens multiplier. */
    fun renderLensLabel(capabilities: CameraRuntimeCapabilities) {
        lensLabel.text = when {
            capabilities.isFront -> context.getString(R.string.camera_lens_front)
            capabilities.zoomMultiplier < ULTRA_WIDE_MAX_MULTIPLIER ->
                context.getString(R.string.camera_lens_ultrawide)
            capabilities.zoomMultiplier > TELE_MIN_MULTIPLIER -> context.getString(R.string.camera_lens_tele)
            else -> context.getString(R.string.camera_lens_wide)
        }
    }

    private fun formatZoomRatio(ratio: Float): String =
        if (ratio % 1f == 0f) "${ratio.toInt()}×" else String.format(Locale.US, "%.1f×", ratio)

    /** Zoom label without the "x" suffix so the preset pills stay narrow (S0753), e.g. "1", "0.5". */
    private fun formatZoomLabel(ratio: Float): String =
        if (ratio % 1f == 0f) "${ratio.toInt()}" else String.format(Locale.US, "%.1f", ratio)

    private companion object {
        const val ZOOM_PILL_MATCH_EPSILON = 0.15f
        const val CHIP_SIDE_PADDING_DP = 10f
        const val CHIP_VERT_PADDING_DP = 4f
        const val CHIP_SPACING_DP = 6f
        const val CHIP_TEXT_SP = 11f
        const val ULTRA_WIDE_MAX_MULTIPLIER = 0.8f
        const val TELE_MIN_MULTIPLIER = 1.5f
    }
}
