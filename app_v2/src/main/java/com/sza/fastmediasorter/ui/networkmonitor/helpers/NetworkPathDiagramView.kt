package com.sza.fastmediasorter.ui.networkmonitor.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.R as MaterialR

/**
 * S1433: one hop of the path the Internet section draws.
 *
 * [reachable] is what the platform actually reports about that hop, never a guess: research artifact 01
 * records that Android exposes no topology past the default route, so a hop with nothing behind it is drawn
 * as unknown rather than as present.
 */
data class NetworkPathNode(
    val label: String,
    val value: String?,
    val reachable: Boolean,
) {
    /** Visible, one-line form. The fragment separately supplies the unabridged accessibility text. */
    val displayText: String
        get() = value?.takeIf(String::isNotBlank)?.let { "$label: $it" } ?: label
}

/**
 * S1433: draws the device -> gateway -> DNS -> internet chain as boxes joined by connectors.
 *
 * Not a chart and not a map. It holds no strings of its own - every label and every value arrives from the
 * section, which also sets the [android.view.View.setContentDescription] text equivalent strategic §11
 * criterion 15 requires, because a canvas has nothing a screen reader can walk.
 *
 * An unreachable hop is drawn dashed as well as dimmed: strategic §3.2 forbids colour as the only
 * differentiator, and this diagram has to survive a grayscale screenshot in a bug report.
 *
 * The chain turns vertical when the available width cannot give each hop a readable box, which is the
 * ordinary case on a phone in portrait with four hops: a horizontal row would ellipsize an IPv4 address
 * down to nothing, and an address nobody can read is not a diagnostic.
 */
class NetworkPathDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var nodes: List<NetworkPathNode> = emptyList()

    private val density = resources.displayMetrics.density

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = NODE_STROKE_DP * density
    }

    private val absentNodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = NODE_STROKE_DP * density
        pathEffect = dash()
    }

    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LINK_STROKE_DP * density
    }

    private val absentLinkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LINK_STROKE_DP * density
        pathEffect = dash()
    }

    /**
     * S1617: sp, not dp. The old line multiplied an sp-named constant by [density], which is the dp
     * conversion - so the label was pinned at 14dp and did not grow one pixel when the user raised the
     * system font size. That is the whole of goal 4: the label was both small and deaf to the setting
     * meant to fix it.
     */
    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(LABEL_TEXT_SP)
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(VALUE_TEXT_SP)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val box = RectF()

    init {
        val outline = themeColor(MaterialR.attr.colorOutline)
        val variant = themeColor(MaterialR.attr.colorOnSurfaceVariant)
        nodePaint.color = outline
        absentNodePaint.color = variant
        linkPaint.color = outline
        absentLinkPaint.color = variant
        labelPaint.color = variant
        valuePaint.color = themeColor(MaterialR.attr.colorOnSurface)
    }

    /** Replaces the whole chain; the section rebuilds it rather than mutating one hop. */
    fun setNodes(value: List<NetworkPathNode>) {
        nodes = value
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val count = nodes.size.coerceAtLeast(1)
        val content = if (isVertical(measuredWidth)) {
            count * nodeHeight() + (count - 1) * connectorLength()
        } else {
            nodeHeight()
        }
        setMeasuredDimension(measuredWidth, (content + paddingTop + paddingBottom).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (nodes.isEmpty()) {
            return
        }
        if (isVertical(width)) {
            drawVertical(canvas)
        } else {
            drawHorizontal(canvas)
        }
    }

    private fun drawHorizontal(canvas: Canvas) {
        val usable = (width - paddingLeft - paddingRight).toFloat()
        val step = usable / nodes.size
        val boxWidth = step - connectorLength()
        val top = paddingTop.toFloat()
        val bottom = top + nodeHeight()
        nodes.forEachIndexed { index, node ->
            val left = paddingLeft + index * step + connectorLength() / 2f
            box.set(left, top, left + boxWidth, bottom)
            drawNode(canvas, node, box)
            if (index > 0) {
                val middle = (top + bottom) / 2f
                canvas.drawLine(left - connectorLength(), middle, left, middle, linkPaintAt(index))
            }
        }
    }

    private fun drawVertical(canvas: Canvas) {
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val step = nodeHeight() + connectorLength()
        nodes.forEachIndexed { index, node ->
            val top = paddingTop + index * step
            box.set(left, top, right, top + nodeHeight())
            drawNode(canvas, node, box)
            if (index > 0) {
                val middle = (left + right) / 2f
                canvas.drawLine(middle, top - connectorLength(), middle, top, linkPaintAt(index))
            }
        }
    }

    /**
     * S1617: the label and its value are two lines, not "Gateway: 192.168.1.1" squeezed into one.
     *
     * One line meant the box had to hold both halves at once, so on a phone the address was ellipsized
     * away - and an address nobody can read is not a diagnostic. Two lines spend height, which this
     * diagram has, instead of width, which it does not.
     */
    private fun drawNode(canvas: Canvas, node: NetworkPathNode, bounds: RectF) {
        val radius = CORNER_DP * density
        canvas.drawRoundRect(bounds, radius, radius, if (node.reachable) nodePaint else absentNodePaint)
        val inner = bounds.width() - INNER_PADDING_DP * density * 2f
        val centerX = bounds.centerX()
        val value = node.value?.takeIf { it.isNotBlank() }
        if (value == null) {
            val baseline = bounds.centerY() - (valuePaint.ascent() + valuePaint.descent()) / 2f
            canvas.drawText(fit(node.label, valuePaint, inner), centerX, baseline, valuePaint)
            return
        }
        val labelHeight = labelPaint.descent() - labelPaint.ascent()
        val valueHeight = valuePaint.descent() - valuePaint.ascent()
        val block = labelHeight + lineGap() + valueHeight
        val top = bounds.centerY() - block / 2f
        canvas.drawText(fit(node.label, labelPaint, inner), centerX, top - labelPaint.ascent(), labelPaint)
        val valueTop = top + labelHeight + lineGap()
        canvas.drawText(fit(value, valuePaint, inner), centerX, valueTop - valuePaint.ascent(), valuePaint)
    }

    private fun lineGap(): Float = LINE_GAP_DP * density

    /** A hop is joined to the previous one with a solid line only when both ends actually answered. */
    private fun linkPaintAt(index: Int): Paint {
        val joined = nodes[index].reachable && nodes[index - 1].reachable
        return if (joined) linkPaint else absentLinkPaint
    }

    private fun fit(text: String, paint: TextPaint, maxWidth: Float): String =
        TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    private fun isVertical(availableWidth: Int): Boolean {
        val usable = availableWidth - paddingLeft - paddingRight
        return usable < nodes.size.coerceAtLeast(1) * MIN_HORIZONTAL_NODE_DP * density
    }

    /**
     * Grows with the font scale rather than pinning a box the text can outgrow: at a large system font
     * a fixed 48dp box would clip both lines, which is the failure goal 4 exists to remove.
     */
    private fun nodeHeight(): Float {
        val text = (labelPaint.descent() - labelPaint.ascent()) +
            lineGap() +
            (valuePaint.descent() - valuePaint.ascent())
        val padded = text + INNER_PADDING_DP * density * 2f
        return maxOf(NODE_HEIGHT_DP * density, padded)
    }

    private fun connectorLength(): Float = CONNECTOR_DP * density

    private fun dash(): DashPathEffect =
        DashPathEffect(floatArrayOf(DASH_ON_DP * density, DASH_OFF_DP * density), 0f)

    private fun themeColor(attr: Int): Int {
        val typed = TypedValue()
        context.theme.resolveAttribute(attr, typed, true)
        return typed.data
    }

    private companion object {

        const val NODE_STROKE_DP = 1.5f
        const val LINK_STROKE_DP = 1.5f
        const val NODE_HEIGHT_DP = 48f
        const val CONNECTOR_DP = 16f
        const val CORNER_DP = 8f
        const val INNER_PADDING_DP = 6f
        const val LABEL_TEXT_SP = 13f
        const val VALUE_TEXT_SP = 17f
        const val LINE_GAP_DP = 2f
        const val DASH_ON_DP = 4f
        const val DASH_OFF_DP = 3f

        /**
         * Below this per-hop width an IPv4 address no longer fits, so the chain stacks instead. Raised
         * with the text in S1617: the old 96dp was measured against a 14dp label that is now 17sp.
         */
        const val MIN_HORIZONTAL_NODE_DP = 128f
    }
}
