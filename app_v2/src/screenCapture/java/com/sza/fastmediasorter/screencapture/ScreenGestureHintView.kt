package com.sza.fastmediasorter.screencapture

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.EdgeGestureAxis
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection

/**
 * S1162: the three-row legend shown next to an edge band while a gesture is in progress.
 *
 * Rows are ordered UP, RIGHT, DOWN - up, straight ahead, down - matching the direction diagram in
 * settings. There are always exactly three: a slot carrying "do nothing" is an action with its own
 * label and icon, not an absence, so the hint never reshapes between zones (ADR-3).
 *
 * Views are built in code rather than inflated. The hosts are Services with no app theme, and the
 * window is drawn over arbitrary other apps - so colours are fixed here instead of resolved from
 * `?attr/`: there is no theme to resolve them against, and the surface underneath is unknown, which
 * is exactly why the panel is opaque-dark with white text.
 */
class ScreenGestureHintView(context: Context) : LinearLayout(context) {

    /** One rendered direction: what the gesture would do and how it is labelled. */
    class HintRow(
        val direction: ScreenshotGestureDirection,
        @DrawableRes val iconRes: Int,
        val label: CharSequence,
    )

    private val rowContainers = LinkedHashMap<ScreenshotGestureDirection, LinearLayout>()
    private var highlighted: ScreenshotGestureDirection? = null

    private val selectedBackground = roundedRect(ROW_SELECTED_COLOR, ROW_CORNER_RADIUS_DP)

    private val rowsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** S1210: the "do nothing" target. Sized for a finger, not for the glyph - it is a drop zone. */
    private val cancelTarget = ImageView(context).apply {
        setImageResource(R.drawable.ic_cancel)
        imageTintList = ColorStateList.valueOf(CONTENT_COLOR)
        val inset = dp(CANCEL_ICON_INSET_DP)
        setPadding(inset, inset, inset, inset)
        val size = dp(CANCEL_TARGET_SIZE_DP)
        layoutParams = LayoutParams(size, size)
    }

    /**
     * S1210: how deep the cancel target reaches, measured from the panel's edge-facing side and
     * including the panel padding in front of it. The touch handler maps a finger this shallow onto
     * cancel, so it must be the same number the target is laid out with - not a second formula.
     */
    val cancelTargetExtentPx: Int = dp(PANEL_PADDING_DP) + dp(CANCEL_TARGET_SIZE_DP)

    init {
        gravity = Gravity.CENTER
        background = roundedRect(PANEL_COLOR, PANEL_CORNER_RADIUS_DP)
        val padding = dp(PANEL_PADDING_DP)
        setPadding(padding, padding, padding, padding)
    }

    /**
     * Rebuilds the panel. The view is created per gesture, so this runs once per hint.
     *
     * [axis] is the axis the band's own edge runs across, and [farEdge] tells which of that axis' two
     * edges the band sits on. Together they place the cancel target on the side facing the screen edge:
     * the panel is anchored by its edge-facing side, which is the leading one for a near-edge band and
     * the trailing one for a far-edge band.
     */
    fun bind(rows: List<HintRow>, axis: EdgeGestureAxis, farEdge: Boolean) {
        orientation = if (axis == EdgeGestureAxis.VERTICAL) HORIZONTAL else VERTICAL
        removeAllViews()
        rowsContainer.removeAllViews()
        rowContainers.clear()
        highlighted = null
        rows.forEach { row -> addRow(row) }
        if (farEdge) {
            addView(rowsContainer)
            addView(cancelTarget)
        } else {
            addView(cancelTarget)
            addView(rowsContainer)
        }
        applyHighlight()
    }

    /**
     * Marks [direction] as the one that would fire on lift; null lights the cancel target instead.
     *
     * S1210: there is no "nothing selected" state - a finger that has not reached any direction is on
     * the cancel target, so the panel always shows exactly one lit item.
     *
     * Runs on every touch move, so it exits early on a no-op and reuses the two background instances
     * built in the constructor instead of allocating per call.
     */
    fun highlight(direction: ScreenshotGestureDirection?) {
        if (highlighted == direction) return
        highlighted = direction
        applyHighlight()
    }

    private fun applyHighlight() {
        rowContainers.forEach { (direction, container) ->
            val selected = direction == highlighted
            // Not colour alone (strategic §3.2): the selected row also gains a filled shape, and the
            // unselected ones recede by opacity - both survive a colourblind viewer and a washed-out panel.
            container.background = if (selected) selectedBackground else null
            container.alpha = if (selected) ALPHA_SELECTED else ALPHA_UNSELECTED
        }
        val cancelSelected = highlighted == null
        cancelTarget.background = if (cancelSelected) selectedBackground else null
        cancelTarget.alpha = if (cancelSelected) ALPHA_SELECTED else ALPHA_UNSELECTED
    }

    private fun addRow(row: HintRow) {
        val iconSize = dp(ICON_SIZE_DP)
        val icon = ImageView(context).apply {
            setImageResource(row.iconRes)
            imageTintList = ColorStateList.valueOf(CONTENT_COLOR)
            layoutParams = LayoutParams(iconSize, iconSize)
        }
        val label = TextView(context).apply {
            text = row.label
            setTextColor(CONTENT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SIZE_SP)
            maxLines = LABEL_MAX_LINES
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(ICON_LABEL_GAP_DP) }
        }
        val container = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val horizontal = dp(ROW_PADDING_HORIZONTAL_DP)
            val vertical = dp(ROW_PADDING_VERTICAL_DP)
            setPadding(horizontal, vertical, horizontal, vertical)
            addView(icon)
            addView(label)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rowsContainer.addView(container)
        rowContainers[row.direction] = container
    }

    private fun roundedRect(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private companion object {
        private const val PANEL_COLOR = 0xE61C1C1C.toInt()
        private const val ROW_SELECTED_COLOR = 0x59FFFFFF
        private const val CONTENT_COLOR = Color.WHITE

        private const val PANEL_CORNER_RADIUS_DP = 12f
        private const val ROW_CORNER_RADIUS_DP = 8f
        private const val PANEL_PADDING_DP = 6f
        private const val ROW_PADDING_HORIZONTAL_DP = 8f
        private const val ROW_PADDING_VERTICAL_DP = 6f
        private const val ICON_SIZE_DP = 22f
        private const val ICON_LABEL_GAP_DP = 8f
        private const val CANCEL_TARGET_SIZE_DP = 48f
        private const val CANCEL_ICON_INSET_DP = 10f
        private const val LABEL_TEXT_SIZE_SP = 13f
        private const val LABEL_MAX_LINES = 2

        private const val ALPHA_SELECTED = 1f
        private const val ALPHA_UNSELECTED = 0.5f
    }
}
