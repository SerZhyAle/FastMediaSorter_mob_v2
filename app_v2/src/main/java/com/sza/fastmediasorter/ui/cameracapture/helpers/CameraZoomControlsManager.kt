package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.common.widget.OutlinedTextView
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
    /** S1261: cross-lens floor pill tap, in equivalent-zoom space (lens switch + zoom, one action). */
    private val onCrossLensFloorSelected: (Float) -> Unit = {},
    /** S1997: camera lens switch button to update active lens icon dynamically. */
    private val switchButton: MaterialButton? = null,
) {

    /** Builds one pill per zoom preset and selects the one nearest the live ratio. */
    fun configure(capabilities: CameraRuntimeCapabilities, liveZoomRatio: Float, liveLinearZoom: Float) {
        presetGroup.removeAllViews()
        // S1189: the lens's own optical limits, at the same precision the presets are rounded to.
        val nativeMin = CameraRuntimeCapabilities.roundToStep(capabilities.minZoomRatio)
        val nativeMax = CameraRuntimeCapabilities.roundToStep(capabilities.maxZoomRatio)
        if (capabilities.showsCrossLensFloor) {
            // S1261: the device's widest equivalent leads the row even though the bound lens cannot
            // reach it - its tap switches optics. Amber: it IS a native minimum, just of another lens.
            val display = capabilities.crossLensFloorDisplay
            val floorPill = buildPill(
                labelValue = display,
                amber = true,
                pillTag = CROSS_LENS_FLOOR_TAG,
            ) { onCrossLensFloorSelected(capabilities.minEquivalentZoomRatio) }
            addPill(floorPill)
        }
        capabilities.zoomPresets.forEach { preset ->
            // S1260: pills print the coarse display rounding (half steps below 5, wholes above) so the
            // row stays readable; the applied preset keeps its precise native ratio.
            val equivalent = CameraRuntimeCapabilities.roundEquivalentForDisplay(
                preset * capabilities.zoomMultiplier,
            )
            val isNativeBound = preset == nativeMin || preset == nativeMax
            val pill = buildPill(
                labelValue = equivalent,
                amber = isNativeBound,
                pillTag = preset,
            ) { onPresetSelected(preset) }
            addPill(pill)
        }
        syncSelection(liveZoomRatio, liveLinearZoom, capabilities.zoomMultiplier)
    }

    /**
     * S1675: the row shown on a lens whose own zoom range is a single point - one pill per rear lens,
     * printed in equivalent values, tap switches the optics. The preset row cannot serve there: with
     * `minZoomRatio == maxZoomRatio` it has nothing to offer, which is why the whole row used to
     * vanish and leave the wide lens without a way back.
     *
     * The pills carry a string tag rather than a Float one, so [syncSelection]'s nearest-ratio match
     * skips them - a lens pill is selected by which lens is bound, never by the live zoom value.
     */
    fun configureLensPills(capabilities: CameraRuntimeCapabilities) {
        presetGroup.removeAllViews()
        val activeFloor = capabilities.ownEquivalentFloorDisplay
        capabilities.rearLensEquivalentFloors.forEach { floor ->
            val pill = buildPill(
                labelValue = floor,
                amber = true,
                pillTag = LENS_FLOOR_TAG_PREFIX + floor,
            ) { onCrossLensFloorSelected(floor) }
            pill.isSelected = abs(floor - activeFloor) < CameraRuntimeCapabilities.ZOOM_EPSILON
            addPill(pill)
        }
    }

    /**
     * Custom see-through pill (the Material chip kept drawing an opaque light surface). The label is
     * the equivalent zoom, so an ultra-wide reads 0.5. S1189: amber marks an optical limit and the
     * description says so too - the distinction must not be colour-only.
     */
    private fun buildPill(
        labelValue: Float,
        amber: Boolean,
        pillTag: Any,
        onClick: () -> Unit,
    ): OutlinedTextView {
        val density = context.resources.displayMetrics.density
        val padH = (density * CHIP_SIDE_PADDING_DP).toInt()
        val padV = (density * CHIP_VERT_PADDING_DP).toInt()
        return OutlinedTextView(context).apply {
            text = formatZoomLabel(labelValue)
            tag = pillTag
            contentDescription = context.getString(
                if (amber) R.string.camera_control_zoom_step_native else R.string.camera_control_zoom_step,
                formatZoomRatio(labelValue),
            )
            isClickable = true
            isFocusable = true
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (amber) R.color.camera_capture_zoom_native_bound else R.color.camera_capture_zoom_step,
                ),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, CHIP_TEXT_SP)
            setPadding(padH, padV, padH, padV)
            background = ContextCompat.getDrawable(context, R.drawable.bg_camera_zoom_chip)
            setOnClickListener { onClick() }
        }
    }

    private fun addPill(pill: OutlinedTextView) {
        val spacing = (context.resources.displayMetrics.density * CHIP_SPACING_DP).toInt()
        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = spacing }
        presetGroup.addView(pill, lp)
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

    /**
     * Active-lens name and icon for the switch button.
     *
     * S1189: ranked inside the reachable lens set rather than guessed from a fixed multiplier
     * threshold; S1997 updates the switch button icon to match active optics in dark conditions.
     */
    fun renderLensLabel(capabilities: CameraRuntimeCapabilities) {
        lensLabel.text = when {
            capabilities.isFront -> context.getString(R.string.camera_lens_front)
            capabilities.activeLensIsMacro -> context.getString(R.string.camera_lens_macro)
            capabilities.activeLensIsWidest -> context.getString(R.string.camera_lens_ultrawide)
            capabilities.zoomMultiplier > CameraRuntimeCapabilities.TELE_MIN_MULTIPLIER ->
                context.getString(R.string.camera_lens_tele)
            else -> context.getString(R.string.camera_lens_wide)
        }
        val iconRes = when {
            capabilities.isFront -> R.drawable.ic_camera_lens_front
            capabilities.activeLensIsMacro -> R.drawable.ic_camera_lens_macro
            capabilities.activeLensIsWidest -> R.drawable.ic_camera_lens_ultrawide
            capabilities.zoomMultiplier > CameraRuntimeCapabilities.TELE_MIN_MULTIPLIER ->
                R.drawable.ic_camera_lens_tele
            else -> R.drawable.ic_camera_lens_wide
        }
        switchButton?.setIconResource(iconRes)
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

        /**
         * S1261: non-Float tag so [syncSelection]'s native-ratio matching skips the cross-lens pill
         * - its value lives in equivalent space of ANOTHER lens; after the switch the rebuilt row
         * carries the same value as a plain native preset and highlights normally.
         */
        const val CROSS_LENS_FLOOR_TAG = "cross_lens_floor"

        /** S1675: same reasoning as [CROSS_LENS_FLOOR_TAG], one tag per rear-lens pill. */
        const val LENS_FLOOR_TAG_PREFIX = "lens_floor_"
    }
}
