package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.res.use
import androidx.core.view.children
import com.sza.fastmediasorter.R

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
 *
 * By default only direct children take part. `svrg_spanDescendants` widens that to every row below
 * plain containers, for a screen whose rows sit in different containers (S4486: the broadcast screen
 * keeps its mode row outside the settings card, because the card is hidden while on air, yet the two
 * must share one value column). Rows then start at different x, so each row's offset from this
 * group's content edge is taken off the column it receives and the values still begin at one x. A
 * group nested under a spanning one leaves the sizing to it.
 */
class SettingsValueRowGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var appliedLabelWidth: Int = -1

    private var spanDescendants: Boolean = false

    init {
        orientation = VERTICAL
        context.obtainStyledAttributes(attrs, R.styleable.SettingsValueRowGroup, defStyleAttr, 0).use { typed ->
            spanDescendants = typed.getBoolean(R.styleable.SettingsValueRowGroup_svrg_spanDescendants, false)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (hasSpanningAncestor()) return
        val participants = collectParticipants()
        val target = resolveLabelColumnWidth(participants)
        if (target != appliedLabelWidth) {
            appliedLabelWidth = target
            participants.forEach { participant ->
                participant.row.applyLabelColumnWidth(columnFor(participant, target))
            }
        }
        // Resolving re-measured the rows' titles and values with UNSPECIFIED specs; skipping this pass
        // when the column is unchanged left a wrapping title laid out as one clipped line (S3860).
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
     * A spanning ancestor already sizes every row below it, this group's included, and a second
     * column computed from a narrower row set would fight it on every measure pass.
     */
    private fun hasSpanningAncestor(): Boolean {
        var ancestor = parent
        while (ancestor is View) {
            if (ancestor is SettingsValueRowGroup && ancestor.spanDescendants) return true
            ancestor = ancestor.parent
        }
        return false
    }

    /**
     * Every [LabelColumnRow] this group sizes, hidden ones included: a row that turns visible later
     * must already carry the column, because the group does not re-apply an unchanged width.
     */
    private fun collectParticipants(): List<Participant> {
        val participants = ArrayList<Participant>()
        collectInto(this, true, 0, 0, participants)
        return participants
    }

    private fun collectInto(
        container: ViewGroup,
        visible: Boolean,
        startOffset: Int,
        endOffset: Int,
        out: MutableList<Participant>,
    ) {
        for (child in container.children) {
            val childVisible = visible && child.visibility != View.GONE
            val margins = child.layoutParams as? MarginLayoutParams
            val marginStart = if (spanDescendants) margins?.marginStart ?: 0 else 0
            val marginEnd = if (spanDescendants) margins?.marginEnd ?: 0 else 0
            if (child is LabelColumnRow) {
                out.add(
                    Participant(
                        child,
                        childVisible,
                        isExcluded(child),
                        startOffset + marginStart,
                        endOffset + marginEnd,
                    )
                )
            } else if (spanDescendants && child is ViewGroup) {
                collectInto(
                    child,
                    childVisible,
                    startOffset + marginStart + child.paddingStart,
                    endOffset + marginEnd + child.paddingEnd,
                    out,
                )
            }
        }
    }

    /**
     * The label width a row receives. The target is measured from this group's content edge, so a row
     * that starts further in gives up the difference; zero stays zero because it means "no column".
     */
    private fun columnFor(participant: Participant, target: Int): Int =
        if (target > 0 && !participant.excluded) (target - participant.startOffset).coerceAtLeast(0) else 0

    /**
     * The shared label width measured from this group's content edge, or 0 when no column should be
     * applied. A row with no label column of its own (a stacked input) reports a zero label and
     * decides nothing.
     */
    private fun resolveLabelColumnWidth(participants: List<Participant>): Int {
        val inner = measuredWidth - paddingStart - paddingEnd
        var widestLabel = 0
        var widestTrailing = 0
        participants.filter { it.visible && !it.excluded }.forEach { participant ->
            val label = participant.row.measureLabelNaturalWidth()
            if (label > 0) {
                val trailing = participant.row.measureTrailingNaturalWidth()
                widestLabel = maxOf(widestLabel, label + participant.startOffset)
                widestTrailing = maxOf(widestTrailing, trailing + participant.endOffset)
            }
        }
        return if (inner > 0 && widestLabel > 0 && widestLabel + widestTrailing <= inner) widestLabel else 0
    }

    /**
     * One row taking part in the column, with how far its edges sit inside this group's content box.
     * Both offsets are zero unless the group spans descendants.
     */
    private class Participant(
        val row: LabelColumnRow,
        val visible: Boolean,
        val excluded: Boolean,
        val startOffset: Int,
        val endOffset: Int,
    )

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
