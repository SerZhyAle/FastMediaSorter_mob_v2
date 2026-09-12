package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * A row that can share one label column with its siblings.
 *
 * Implemented by the settings value rows so [SettingsValueRowGroup] can line their values up without
 * knowing which widget class drew each row.
 */
interface LabelColumnRow {

    /**
     * Width the row's own label (title plus its help icon) wants, ignoring any column width already
     * applied. Measured on demand, so a row never reports back the value the group just gave it -
     * that feedback is what would turn the group's second measure pass into a loop.
     */
    fun measureLabelNaturalWidth(): Int

    /**
     * Width everything right of the label needs at full length: the value, its gap and the trailing
     * glyph. The group adds this to the label column to decide whether a column fits at all.
     */
    fun measureTrailingNaturalWidth(): Int

    /**
     * Pins the row's label column to [widthPx] so the value starts at the same offset in every row.
     * Zero restores the row's own hug-the-content layout, which is the correct answer whenever the
     * column would not fit.
     */
    fun applyLabelColumnWidth(widthPx: Int)
}

/**
 * Vertical container that gives every [LabelColumnRow] child the same label width - the widest label
 * among them - so their values and trailing glyphs line up in a column.
 *
 * Two rules, in this order, both learned from the owner's own screenshots:
 *
 * 1. The column is sized by the longest LABEL, never by the group's width. On a landscape phone the
 *    settings card is over 2000 px wide, and a value pushed to that edge sits far enough from its
 *    caption that the eye loses the row on the way across (owner ruling 2026-09-01).
 * 2. The column is applied only when every row still fits inside it at full length. A fixed label
 *    column plus a long value plus a glyph does not fit a portrait phone: the first attempt wrapped
 *    one value onto a second line and pushed two chevrons off the screen edge. Alignment is worth
 *    having only while it costs nothing - when it does not fit, every row keeps its own hug layout
 *    and the values simply sit next to their captions, which is the property that actually matters.
 */
class SettingsValueRowGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var appliedLabelWidth: Int = -1

    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val target = resolveLabelColumnWidth()
        if (target == appliedLabelWidth) return
        appliedLabelWidth = target
        Timber.d("S2389: shared label column resolved to %d px", target)
        children.forEach { child ->
            (child as? LabelColumnRow)?.applyLabelColumnWidth(if (isExcluded(child)) 0 else target)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams = LayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): LayoutParams = LayoutParams(p)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    /**
     * Whether [child] opted out of the shared column. A child laid out with anything but this group's
     * own params never opted out, which is what a programmatically added row gets.
     */
    private fun isExcluded(child: View): Boolean =
        (child.layoutParams as? LayoutParams)?.excludeFromLabelColumn == true

    /**
     * The shared label width, or 0 when no column should be applied.
     */
    private fun resolveLabelColumnWidth(): Int {
        val rows = children
            .filter { child -> child.visibility != View.GONE && !isExcluded(child) }
            .filterIsInstance<LabelColumnRow>()
            .toList()
        val available = measuredWidth - paddingStart - paddingEnd
        if (rows.isEmpty() || available <= 0) return 0
        val widestLabel = rows.maxOf { row -> row.measureLabelNaturalWidth() }
        val widestTrailing = rows.maxOf { row -> row.measureTrailingNaturalWidth() }
        return if (widestLabel > 0 && widestLabel + widestTrailing <= available) widestLabel else 0
    }

    /**
     * Child parameters of [SettingsValueRowGroup].
     *
     * A row with `layout_excludeFromLabelColumn` belongs to the row above it rather than to the block:
     * its caption and value are unrelated in length to its neighbours', so it keeps its own hug layout
     * and its widths are ignored when the shared column is sized.
     */
    class LayoutParams : LinearLayout.LayoutParams {

        val excludeFromLabelColumn: Boolean

        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
            val typed = context.obtainStyledAttributes(attrs, R.styleable.SettingsValueRowGroup_Layout)
            excludeFromLabelColumn = typed.getBoolean(
                R.styleable.SettingsValueRowGroup_Layout_layout_excludeFromLabelColumn,
                false,
            )
            typed.recycle()
        }

        constructor(width: Int, height: Int) : super(width, height) {
            excludeFromLabelColumn = false
        }

        constructor(source: ViewGroup.LayoutParams) : super(source) {
            excludeFromLabelColumn = false
        }
    }
}
