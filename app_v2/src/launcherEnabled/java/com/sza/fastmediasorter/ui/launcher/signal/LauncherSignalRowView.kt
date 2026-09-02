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
 *
 * S1431 ADR-3: a child now belongs to the row by role rather than by its number. One view may be pinned at
 * each edge - the clock at the start, the device indicators at the end - and holds that edge whatever the
 * signals do; the chips flow in the space the pinned pair leaves. Child order is therefore an invariant:
 * start-pinned view, chip flow, end-pinned view. With neither slot filled the row behaves exactly as it did
 * before the slots existed.
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

    /** Chips in the start-edge group; the rest of the FLOW children belong to the end-edge group. */
    private var startGroupCount = 0

    private var pinnedStart: View? = null
    private var pinnedEnd: View? = null

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

    /**
     * Pins [view] flush against the start padding, ahead of every chip. `null` clears the slot. Callable
     * only by the strip's owning manager - S1421 ADR-2 keeps this row under a single content author.
     */
    fun setPinnedStart(view: View?) {
        pinnedStart = replacePinned(pinnedStart, view, index = 0)
        rebuild()
    }

    /** Pins [view] flush against the end padding, after every chip. `null` clears the slot. */
    fun setPinnedEnd(view: View?) {
        pinnedEnd = replacePinned(pinnedEnd, view, index = childCount)
        rebuild()
    }

    private fun replacePinned(current: View?, next: View?, index: Int): View? {
        if (current === next) {
            return current
        }
        current?.let { removeView(it) }
        next?.let {
            // nextFocus*Id addresses a view by id, so an id-less pinned view would break the focus chain
            // at its own position rather than merely skipping itself.
            if (it.id == NO_ID) {
                it.id = generateViewId()
            }
            addView(it, index.coerceIn(0, childCount))
        }
        return next
    }

    /** First child that belongs to the chip flow. */
    private val flowFrom: Int
        get() = if (pinnedStart == null) 0 else 1

    private val flowCount: Int
        get() = childCount - flowFrom - if (pinnedEnd == null) 0 else 1

    private fun flowChildAt(index: Int): View = getChildAt(flowFrom + index)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val rowHeight = maxOf(MeasureSpec.getSize(heightMeasureSpec), chipSize)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != GONE) {
                measurePart(child, availableWidth, rowHeight)
            }
        }
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(chipSize, heightMeasureSpec),
        )
    }

    /**
     * A chip is square by definition and keeps the exact spec it always had. A pinned view declares
     * WRAP_CONTENT and is measured at its natural size instead: the clock's width is its own text, which a
     * square spec would crop to the first two glyphs.
     */
    private fun measurePart(child: View, availableWidth: Int, rowHeight: Int) {
        if (child.layoutParams?.width == LayoutParams.WRAP_CONTENT) {
            child.measure(
                MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.AT_MOST),
            )
        } else {
            val chipSpec = MeasureSpec.makeMeasureSpec(chipSize, MeasureSpec.EXACTLY)
            child.measure(chipSpec, chipSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        pinnedStart?.takeIf { it.visibility != GONE }?.let { layoutCentred(it, paddingStart) }
        pinnedEnd?.takeIf { it.visibility != GONE }
            ?.let { layoutCentred(it, width - paddingEnd - it.measuredWidth) }
        // The end group is right-aligned as a block rather than filled backwards from the edge, so its
        // children keep submit order left to right - focus order follows child order, and a reversed group
        // would step the D-pad backwards through it.
        val endGroupCount = flowCount - startGroupCount
        var startX = paddingStart + pinnedExtent(pinnedStart)
        var endX = width - paddingEnd - pinnedExtent(pinnedEnd) - groupWidth(startGroupCount, endGroupCount)
        for (index in 0 until flowCount) {
            val child = flowChildAt(index)
            if (index < startGroupCount) {
                layoutCentred(child, startX)
                startX += child.measuredWidth + chipSpacing
            } else {
                layoutCentred(child, endX)
                endX += child.measuredWidth + chipSpacing
            }
        }
    }

    private fun layoutCentred(child: View, x: Int) {
        val top = (height - child.measuredHeight) / 2
        child.layout(x, top, x + child.measuredWidth, top + child.measuredHeight)
    }

    /** Width of [count] flow children starting at flow index [from], the gaps between them included. */
    private fun groupWidth(from: Int, count: Int): Int {
        if (count <= 0) {
            return 0
        }
        var total = (count - 1) * chipSpacing
        for (index in from until from + count) {
            total += flowChildAt(index).measuredWidth
        }
        return total
    }

    /**
     * What a pinned view costs the chip flow: its own width plus the one gap that separates it from the
     * nearest chip. An empty slot costs nothing, which is what keeps an unpinned row identical to the row
     * that existed before the slots. A GONE pinned view costs nothing either - the tray renderer hides the
     * clock when its own switch is off, and the chips must then be free to use that width.
     */
    private fun pinnedExtent(view: View?): Int =
        if (view == null || view.visibility == GONE) 0 else pinnedWidth(view) + chipSpacing

    /**
     * [rebuild] runs on size change and on every signal emission, both of which can precede the first
     * measure of a freshly pinned view. Measuring it here rather than trusting a zero keeps the chip
     * capacity from being computed as though the clock took no room.
     */
    private fun pinnedWidth(view: View): Int {
        if (view.measuredWidth == 0) {
            measurePart(view, width, maxOf(height, chipSize))
        }
        return view.measuredWidth
    }

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
        val startCapacity = capacityIn(paddingStart + pinnedExtent(pinnedStart), gap.first)
        val endCapacity = capacityIn(gap.second, width - paddingEnd - pinnedExtent(pinnedEnd))
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
        shown.forEachIndexed { index, signal -> bindChip(flowChildAt(index), signal) }
        if (hidden > 0 && capacity > 0) {
            bindCounter(flowChildAt(flowCount - 1), hidden)
        }
        // The counter rides the end group, so its slot comes off the end capacity before the split -
        // without that, a full row plus its counter still reaches one slot across the cutout's right edge.
        // An end side too narrow for even the counter keeps the counter with the start group instead,
        // because a right-aligned block with nowhere to fit would still reach across the cutout.
        val counterSlot = if (hidden > 0 && capacity > 0) 1 else 0
        startGroupCount = if (counterSlot == 1 && endCapacity == 0) {
            flowCount
        } else {
            allocateStartGroupCount(chipCount, startCapacity, endCapacity - counterSlot)
        }
        applyFocusOrder()
        requestLayout()
    }

    /**
     * Chains the row left to right and sends D-pad Down to the desktop, so the strip can be entered and
     * left without a touchscreen (strategic §3.1, Rule 16). A pinned view that cannot take focus is left
     * out of the chain - or is hidden by its own switch - is left out instead of ending it, so the chips
     * behind it stay reachable. No focus outline is
     * drawn here - `FocusDecorationController` decorates the focused view app-wide, and a second one would
     * double-draw.
     */
    private fun applyFocusOrder() {
        val chain = mutableListOf<View>()
        pinnedStart?.takeIf { it.takesFocus() }?.let { chain.add(it) }
        for (index in 0 until flowCount) {
            chain.add(flowChildAt(index))
        }
        pinnedEnd?.takeIf { it.takesFocus() }?.let { chain.add(it) }
        chain.forEachIndexed { index, view ->
            view.nextFocusLeftId = chain.getOrNull(index - 1)?.id ?: NO_ID
            view.nextFocusRightId = chain.getOrNull(index + 1)?.id ?: NO_ID
            view.nextFocusDownId = R.id.launcherGridScroll
        }
    }

    private fun View.takesFocus(): Boolean = isFocusable && visibility != GONE

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
     * The chip flow is rebuilt from scratch rather than patched, because the counter's presence shifts what
     * every later child is: keeping a stale counter as a chip, or the reverse, would need a cast that cannot
     * fail safely. The pinned views are deliberately spared - the indicator row holds live Bluetooth, SIM,
     * network and battery subscriptions, and detaching it on every signal emission would tear those down and
     * rebuild them on a permanently visible surface (strategic §3.2).
     */
    private fun syncChildren(chipCount: Int, showCounter: Boolean) {
        val existing = flowCount
        if (existing > 0) {
            removeViews(flowFrom, existing)
        }
        repeat(chipCount) { addFlowChild(R.layout.launcher_signal_chip) }
        if (showCounter) {
            addFlowChild(R.layout.launcher_signal_counter)
        }
    }

    private fun addFlowChild(layoutRes: Int) {
        val child = LayoutInflater.from(context).inflate(layoutRes, this, false)
        // Every chip inflates with the same layout id, and nextFocus*Id addresses views by id - without a
        // unique one the whole row would be one focus target as far as the D-pad is concerned.
        child.id = generateViewId()
        // Inserted before the end-pinned view, so the start-pinned / flow / end-pinned child order holds.
        addView(child, flowFrom + flowCount)
    }

    private fun bindCounter(counter: View, hidden: Int) {
        val text = context.getString(R.string.launcher_signal_overflow_count, hidden)
        (counter as TextView).text = text
        counter.contentDescription = text
        counter.setOnClickListener { onOverflowTap() }
    }

    private fun bindChip(chip: View, signal: LauncherSignal) {
        LauncherSignalIconBinder.bind(chip as ImageView, signal.icon)
        // The chip is an icon and nothing else, so its spoken form is the only place the signal's detail can
        // reach a screen reader - strategic §3.3 of S1465 requires a foreign chip to announce the source
        // application and how many notifications it has, and the label alone carries only the first.
        chip.contentDescription = listOfNotNull(signal.label, signal.detail?.takeIf { it.isNotBlank() })
            .joinToString(separator = ", ")
        if (canOpen(signal)) {
            chip.setOnClickListener { onTap(signal) }
        } else {
            chip.setOnClickListener(null)
            chip.isClickable = false
        }
    }
}

/**
 * S2244: how many of [chipCount] chips the start group holds when each edge group is bounded by its own
 * side's capacity against the cutout span. The end group receives the rest.
 *
 * The half-share the row always used is kept while it fits, so the row looks unchanged until the end side
 * cannot take its half - the case this corrects laid those chips out across the cutout's right edge,
 * under the camera. Pure and free of view state so the allocation rule is testable without a layout.
 */
internal fun allocateStartGroupCount(chipCount: Int, startCapacity: Int, endCapacity: Int): Int =
    minOf(startCapacity, (chipCount + 1) / 2)
        .coerceAtLeast(chipCount - endCapacity)
        .coerceIn(0, chipCount)
