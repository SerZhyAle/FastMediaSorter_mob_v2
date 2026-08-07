package com.sza.fastmediasorter.ui.launcher.signal

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.sza.fastmediasorter.R

/**
 * S1421 ADR-3: the signal icons, laid out as two groups pressed to the edges with the display cutout's own
 * span left free between them.
 *
 * The gap is measured, never assumed: it comes from the cutout's bounding rect rather than from a per-device
 * constant, so a device without a cutout gets the same row unbroken and an off-centre punch-hole is cleared
 * where it actually is. Scrolling is not an option here - a row that must keep a hole in its middle cannot
 * scroll through it (strategic §5.1), which is why overflow collapses into a counter instead.
 */
class LauncherSignalRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    private val chipSize = resources.getDimensionPixelSize(R.dimen.launcher_signal_chip_size)
    private val chipSpacing = resources.getDimensionPixelSize(R.dimen.launcher_signal_chip_spacing)
    private val windowLocation = IntArray(2)

    private var signals: List<LauncherSignal> = emptyList()
    private var canOpen: (LauncherSignal) -> Boolean = { false }
    private var onTap: (LauncherSignal) -> Unit = {}
    private var onOverflowTap: () -> Unit = {}
    private var cutoutBounds = Rect()

    /** How many chips the current width and cutout allow. Phase 05 turns the remainder into a counter. */
    private var capacity = 0

    /** Chips in the start-edge group; the rest of the children belong to the end-edge group. */
    private var startGroupCount = 0

    init {
        // The chips own the focus, not this container - otherwise D-pad would stop on the row itself first.
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }

    /**
     * @param canOpen whether a signal has a screen behind it. A signal without one keeps its chip and stays
     * visible - no signal disappears silently (strategic §5.1) - but the chip is not clickable, so its
     * ripple never promises an action that does not happen.
     */
    fun submit(
        signals: List<LauncherSignal>,
        canOpen: (LauncherSignal) -> Boolean,
        onTap: (LauncherSignal) -> Unit,
    ) {
        this.signals = signals
        this.canOpen = canOpen
        this.onTap = onTap
        rebuild()
    }

    /** What the "+N" chip does. Set once by the strip's owner; the row itself opens nothing. */
    fun setOnOverflowTap(listener: () -> Unit) {
        onOverflowTap = listener
    }

    fun setCutoutBounds(bounds: Rect) {
        if (cutoutBounds == bounds) {
            return
        }
        cutoutBounds = Rect(bounds)
        rebuild()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val childSpec = MeasureSpec.makeMeasureSpec(chipSize, MeasureSpec.EXACTLY)
        for (index in 0 until childCount) {
            getChildAt(index).measure(childSpec, childSpec)
        }
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(chipSize, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val top = (height - chipSize) / 2
        // The end group is right-aligned as a block rather than filled backwards from the edge, so its
        // children keep submit order left to right - focus order (step 04.5) follows child order, and a
        // reversed group would step the D-pad backwards through it.
        val endGroupCount = childCount - startGroupCount
        var startX = paddingStart
        var endX = width - paddingEnd - groupWidth(endGroupCount)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (index < startGroupCount) {
                child.layout(startX, top, startX + chipSize, top + chipSize)
                startX += chipSize + chipSpacing
            } else {
                child.layout(endX, top, endX + chipSize, top + chipSize)
                endX += chipSize + chipSpacing
            }
        }
    }

    private fun groupWidth(count: Int): Int =
        if (count <= 0) 0 else count * chipSize + (count - 1) * chipSpacing

    /**
     * Rebuilds children whenever the inputs that decide how many fit change. Done here rather than inside
     * `onMeasure` so that measure never adds or removes views, which is what makes a second layout pass
     * necessary and a row flicker.
     */
    private fun rebuild() {
        if (width == 0) {
            return
        }
        val gap = localCutoutGap()
        val startCapacity = capacityIn(paddingStart, gap.first)
        val endCapacity = capacityIn(gap.second, width - paddingEnd)
        capacity = startCapacity + endCapacity
        // The counter takes a slot of its own, so one fewer signal is drawn when it appears. Nothing is
        // dropped silently: what the row cannot show, the counter stands for and the sheet lists.
        val chipCount = when {
            signals.size <= capacity -> signals.size
            capacity == 0 -> 0
            else -> capacity - 1
        }
        val hidden = signals.size - chipCount
        val shown = signals.take(chipCount)
        syncChildren(chipCount, showCounter = hidden > 0 && capacity > 0)
        shown.forEachIndexed { index, signal -> bindChip(getChildAt(index), signal) }
        if (hidden > 0 && capacity > 0) {
            bindCounter(getChildAt(childCount - 1), hidden)
        }
        startGroupCount = minOf(startCapacity, (childCount + 1) / 2)
        applyFocusOrder()
        requestLayout()
    }

    /**
     * Chains the chips left to right and sends D-pad Down to the desktop, so the strip can be entered and
     * left without a touchscreen (strategic §3.1, Rule 16). No focus outline is drawn here -
     * `FocusDecorationController` decorates the focused view app-wide, and a second one would double-draw.
     */
    private fun applyFocusOrder() {
        for (index in 0 until childCount) {
            val chip = getChildAt(index)
            chip.nextFocusLeftId = getChildAt(index - 1)?.id ?: NO_ID
            chip.nextFocusRightId = getChildAt(index + 1)?.id ?: NO_ID
            chip.nextFocusDownId = R.id.launcherGridScroll
        }
    }

    /** @return the cutout's start and end in this view's own coordinates, collapsed to a point when absent. */
    private fun localCutoutGap(): Pair<Int, Int> {
        if (cutoutBounds.isEmpty) {
            val middle = width / 2
            return middle to middle
        }
        getLocationInWindow(windowLocation)
        val originX = windowLocation[0]
        return (cutoutBounds.left - originX).coerceIn(0, width) to
            (cutoutBounds.right - originX).coerceIn(0, width)
    }

    private fun capacityIn(from: Int, to: Int): Int {
        val available = to - from
        if (available < chipSize) {
            return 0
        }
        return 1 + (available - chipSize) / (chipSize + chipSpacing)
    }

    /**
     * Rebuilt from scratch rather than patched, because the counter's presence shifts what every later child
     * is: keeping a stale counter as a chip, or the reverse, would need a cast that cannot fail safely.
     */
    private fun syncChildren(chipCount: Int, showCounter: Boolean) {
        removeAllViews()
        repeat(chipCount) { addChild(R.layout.launcher_signal_chip) }
        if (showCounter) {
            addChild(R.layout.launcher_signal_counter)
        }
    }

    private fun addChild(layoutRes: Int) {
        val child = LayoutInflater.from(context).inflate(layoutRes, this, false)
        // Every chip inflates with the same layout id, and nextFocus*Id addresses views by id - without a
        // unique one the whole row would be one focus target as far as the D-pad is concerned.
        child.id = generateViewId()
        addView(child)
    }

    private fun bindCounter(counter: View, hidden: Int) {
        val text = context.getString(R.string.launcher_signal_overflow_count, hidden)
        (counter as TextView).text = text
        counter.contentDescription = text
        counter.setOnClickListener { onOverflowTap() }
    }

    private fun bindChip(chip: View, signal: LauncherSignal) {
        (chip as ImageView).setImageResource(signal.iconRes)
        chip.contentDescription = signal.label
        if (canOpen(signal)) {
            chip.setOnClickListener { onTap(signal) }
        } else {
            chip.setOnClickListener(null)
            chip.isClickable = false
        }
    }
}
