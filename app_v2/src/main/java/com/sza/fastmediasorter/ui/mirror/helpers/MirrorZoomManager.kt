package com.sza.fastmediasorter.ui.mirror.helpers

import android.graphics.Typeface
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureSessionManager
import kotlin.math.abs

/**
 * Owns the mirror's zoom presets (strategic S1924 2.3).
 *
 * The presets are a list, not four buttons in the layout - strategic 5.3 requires a fifth value to cost
 * no layout edit, so the row is built from [PRESET_RATIOS] and one entry is inflated per ratio.
 *
 * The active preset is marked by three cues at once, because 3.2 forbids leaning on colour alone: the
 * selected background (a filled body plus a thicker outline), a bold label, and the selected state the
 * accessibility services read.
 */
class MirrorZoomManager(
    private val container: LinearLayout,
    private val sessionManager: CameraCaptureSessionManager,
    private val onRatioPicked: (Float) -> Unit,
) {

    private val presets = mutableListOf<TextView>()

    private var activeRatio: Float = PRESET_RATIOS.first()

    private var tint: Int? = null

    /** Builds one control per preset. Safe to call once per screen; a second call rebuilds the row. */
    fun attach() {
        container.removeAllViews()
        presets.clear()
        val inflater = LayoutInflater.from(container.context)
        PRESET_RATIOS.forEach { ratio ->
            val view = inflater.inflate(R.layout.item_mirror_zoom_preset, container, false) as TextView
            val label = formatRatio(ratio)
            view.text = container.context.getString(R.string.mirror_zoom_preset_label, label)
            view.contentDescription = container.context.getString(R.string.mirror_zoom_button, label)
            view.setOnClickListener { apply(ratio) }
            container.addView(view)
            presets += view
        }
        render()
    }

    /** Applies [ratio] to the session and reports it back so the host can persist it. */
    fun apply(ratio: Float) {
        activeRatio = ratio
        sessionManager.setZoomRatio(ratio)
        render()
        onRatioPicked(ratio)
    }

    /**
     * Shows [ratio] as active without re-applying it - the path a restored setting takes, where the
     * session is told the zoom by the bind itself and telling it twice would cost a second rebind.
     */
    fun showActive(ratio: Float) {
        activeRatio = nearestPreset(ratio)
        render()
    }

    /**
     * The presets sit on the glow field like every other control, so their colour follows the field
     * rather than the theme - see [com.sza.fastmediasorter.ui.mirror.MirrorActivity.applyControlTint].
     * Held so a row rebuilt by [attach] does not fall back to the layout's default.
     */
    fun applyTint(color: Int) {
        tint = color
        render()
    }

    private fun render() {
        presets.forEachIndexed { index, view ->
            val selected = PRESET_RATIOS[index] == activeRatio
            view.isSelected = selected
            view.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            tint?.let { view.setTextColor(it) }
        }
    }

    /** A stored ratio need not be one of the presets - an older build may have written another. */
    private fun nearestPreset(ratio: Float): Float =
        PRESET_RATIOS.minByOrNull { abs(it - ratio) } ?: PRESET_RATIOS.first()

    /** "3", not "3.0": the label is read at a glance and the fraction is never meaningful here. */
    private fun formatRatio(ratio: Float): String =
        if (ratio == ratio.toInt().toFloat()) ratio.toInt().toString() else ratio.toString()

    private companion object {
        val PRESET_RATIOS = listOf(1f, 2f, 3f, 5f)
    }
}
