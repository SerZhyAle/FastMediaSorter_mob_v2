package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.res.use
import com.sza.fastmediasorter.R

/**
 * Shared container for two adjacent form inputs (e.g. Host + Port, Username + Password).
 *
 * Replaces the repeated manual `layout_weight` / `marginStart` / `marginEnd` tuning that was
 * copy-pasted across the resource-entry layouts (S0567 survey item 4). Exactly two child views
 * are laid out side-by-side with a preset weight ratio and a preset inter-field gap.
 *
 * Public XML attributes use the `ffp_` prefix (see `attrs.xml`):
 * - `ffp_ratio` - `one_one` (1:1, default) or `two_one` (first field twice as wide).
 * - `ffp_stackThreshold` - optional min width below which the pair stacks vertically.
 *
 * Children declare `layout_width="0dp"`; this view assigns the weights, so per-field weight
 * attributes in XML are unnecessary.
 */
class FormFieldPairLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var ratio: Ratio = Ratio.ONE_ONE
    private var stackThresholdPx: Int = 0
    private var stacked = false
    private val spacingPx = resources.getDimensionPixelSize(R.dimen.form_field_pair_spacing)

    enum class Ratio(val firstWeight: Float, val secondWeight: Float) {
        ONE_ONE(1f, 1f),
        TWO_ONE(2f, 1f),
    }

    init {
        orientation = HORIZONTAL
        // Inputs of different heights (e.g. a field with helper text vs one without) should not
        // shift their baselines; align by box instead.
        isBaselineAligned = false
        if (attrs != null) {
            context.obtainStyledAttributes(attrs, R.styleable.FormFieldPairLayout, defStyleAttr, 0).use { ta ->
                ratio = when (ta.getInt(R.styleable.FormFieldPairLayout_ffp_ratio, 0)) {
                    1 -> Ratio.TWO_ONE
                    else -> Ratio.ONE_ONE
                }
                stackThresholdPx = ta.getDimensionPixelSize(R.styleable.FormFieldPairLayout_ffp_stackThreshold, 0)
            }
        }
    }

    /**
     * Sets the weight ratio at runtime; re-applies child weights immediately.
     */
    fun setRatio(value: Ratio) {
        if (ratio == value) return
        ratio = value
        if (!stacked) applyHorizontalWeights()
        requestLayout()
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        if (childCount in 1..2) applyChildLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (stackThresholdPx > 0) {
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
            val shouldStack = availableWidth in 1 until stackThresholdPx
            if (shouldStack != stacked) {
                stacked = shouldStack
                applyChildLayout()
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun applyChildLayout() {
        orientation = if (stacked) VERTICAL else HORIZONTAL
        if (stacked) applyStackedLayout() else applyHorizontalWeights()
    }

    private fun applyHorizontalWeights() {
        val first = getChildAt(0)
        val second = getChildAt(1)
        first?.let { applyWeight(it, width = 0, weight = ratio.firstWeight, leadingGap = false) }
        second?.let { applyWeight(it, width = 0, weight = ratio.secondWeight, leadingGap = true) }
    }

    private fun applyStackedLayout() {
        val first = getChildAt(0)
        val second = getChildAt(1)
        first?.let { applyWeight(it, width = LayoutParams.MATCH_PARENT, weight = 0f, leadingGap = false) }
        second?.let { applyWeight(it, width = LayoutParams.MATCH_PARENT, weight = 0f, leadingGap = true, stacked = true) }
    }

    private fun applyWeight(
        child: View,
        width: Int,
        weight: Float,
        leadingGap: Boolean,
        stacked: Boolean = false,
    ) {
        val lp = (child.layoutParams as? LayoutParams) ?: LayoutParams(width, LayoutParams.WRAP_CONTENT)
        lp.width = width
        lp.weight = weight
        lp.marginStart = if (leadingGap && !stacked) spacingPx else 0
        lp.topMargin = if (leadingGap && stacked) spacingPx else 0
        child.layoutParams = lp
    }
}
