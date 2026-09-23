package com.sza.fastmediasorter.ui.common.widget.dimclock

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DimClockOverlayBinding
import com.sza.fastmediasorter.databinding.ItemDimStatusChipBinding
import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S3256: Reusable clock and status overlay for dimmed screens on phone (top-left aligned).
 *
 * Displays:
 * 1) Top notification chip row (from DimStatusContentProvider chips)
 * 2) Clock with weekday and short date (styled to match ClockGadget)
 * 3) Status row with battery rectangle (fill and color by level, charging bolt) + network/status chips
 *
 * Inert by construction: touches fall through to DimOverlayView (isClickable = false, isFocusable = false).
 * Window insets safe bounds are applied as padding.
 * Burn-in position shift and alpha auto-fade are supported.
 */
class DimClockOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = DimClockOverlayBinding.inflate(LayoutInflater.from(context), this, true)

    private var styleProvider: DimClockStyleProvider? = null
    private var statusProvider: DimStatusContentProvider? = null
    private var unitSystemProvider: UnitSystemProvider? = null
    private var iconLoader: DimChipIconLoader? = null
    private var actionRouter: DimChipActionRouter? = null
    private var interactionHandler: DimClockInteractionHandler? = null

    private var observationJob: Job? = null
    private var currentUnitSystem: UnitSystem = UnitSystem.METRIC
    private val ticker = DimClockTicker()
    private var currentStyle: DimClockStyle? = null

    /**
     * S3366: invoked by a chip or battery tap before its intent starts - ADR-2's ordering, so the
     * target activity is never drawn above a lit dim overlay at minimum brightness. The host wires
     * this to the same dismissal path its dim overlay's exit gesture uses.
     */
    var onDimExitRequested: (() -> Unit)? = null

    var onUnhandledMotionEvent: ((MotionEvent) -> Unit)? = null

    init {
        isClickable = false
        isFocusable = false
        contentDescription = context.getString(R.string.dim_clock_status_cd)

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            applySafePadding(insets)
            insets
        }
    }

    /**
     * Rule 17 safe bounds. The dim screen hides the system bars, and a hidden bar reports a zero inset,
     * so the bars are read ignoring visibility: the status bar's height is what clears the camera cutout.
     */
    private fun applySafePadding(insets: WindowInsetsCompat) {
        val padding = contentPaddingFor(
            barsIgnoringVisibility = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()),
            cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()),
            basePadding = resources.getDimensionPixelSize(R.dimen.dim_clock_padding),
            minimumShift = resources.getDimensionPixelSize(R.dimen.dim_clock_edge_offset),
            isRtl = layoutDirection == LAYOUT_DIRECTION_RTL,
        )
        Timber.d("S3369: dim clock padding start=${padding.left} top=${padding.top}")
        binding.dimClockContentBlock.setPaddingRelative(padding.left, padding.top, padding.right, padding.bottom)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onHostInteraction()
        if (super.dispatchTouchEvent(event)) return true
        onUnhandledMotionEvent?.invoke(event)
        return true
    }

    /**
     * Injects dependencies and starts observing style and status.
     *
     * S3366: [iconLoader], [actionRouter] and [interactionHandler] are null in the non-launcher
     * flavor - there a chip keeps its static fallback icon, stays inert, and the clock block keeps
     * no gestures, which is that flavor's frozen behavior.
     */
    fun bind(
        styleProvider: DimClockStyleProvider,
        statusProvider: DimStatusContentProvider,
        unitSystemProvider: UnitSystemProvider? = null,
        iconLoader: DimChipIconLoader? = null,
        actionRouter: DimChipActionRouter? = null,
        interactionHandler: DimClockInteractionHandler? = null,
    ) {
        this.styleProvider = styleProvider
        this.statusProvider = statusProvider
        this.unitSystemProvider = unitSystemProvider
        this.iconLoader = iconLoader
        this.actionRouter = actionRouter
        this.interactionHandler = interactionHandler
        applyStyle(styleProvider.getStyle())
        setupBatteryBox()
        setupClockBlock()
        startObserving()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // This view joins the decor after the window's inset pass, which never re-runs for a late child
        // on its own - the S3369 padding stayed at its XML default until the root insets were read here.
        ViewCompat.getRootWindowInsets(this)?.let(::applySafePadding)
        ViewCompat.requestApplyInsets(this)
        startObserving()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        observationJob?.cancel()
        observationJob = null
    }

    private fun startObserving() {
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        observationJob?.cancel()
        observationJob = lifecycleOwner.lifecycleScope.launch {
            styleProvider?.let { applyStyle(it.getStyle()) }

            unitSystemProvider?.let { provider ->
                launch {
                    provider.current.collectLatest { system ->
                        currentUnitSystem = system
                        styleProvider?.let { applyStyle(it.getStyle()) }
                    }
                }
            }

            statusProvider?.let { provider ->
                launch {
                    provider.observeStatus().collectLatest { snapshot ->
                        renderStatus(snapshot)
                    }
                }
            }

            launch {
                while (isActive) {
                    applyTickerEffects()
                    delay(ticker.getCadenceMs(currentStyle?.secondsVisible == true))
                }
            }
        }
    }

    /**
     * Applies visual styling from DimClockStyle (typeface, dial color, seconds visibility).
     */
    fun applyStyle(style: DimClockStyle) {
        currentStyle = style
        val timePattern = UnitScale.timePattern(currentUnitSystem, style.secondsVisible)
        val datePattern = WEEKDAY_FIELD + UnitScale.shortDatePattern(currentUnitSystem)

        applyPattern(binding.dimClockTime, timePattern)
        applyPattern(binding.dimClockDate, datePattern)

        val dialColor = style.dialColor ?: Color.WHITE
        binding.dimClockTime.setTextColor(dialColor)
        binding.dimClockDate.setTextColor(dialColor)

        binding.dimClockTime.typeface = resolveTypeface(style.dialTypeface)
        binding.dimClockDate.typeface = resolveTypeface(style.dialTypeface)
    }

    private fun applyPattern(clock: TextClock, pattern: String) {
        val imperial = currentUnitSystem == UnitSystem.IMPERIAL
        clock.format12Hour = pattern.takeIf { imperial }
        clock.format24Hour = pattern.takeIf { !imperial }
    }

    private fun resolveTypeface(persistedName: String): Typeface = when (persistedName) {
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        "serif" -> Typeface.create("serif", Typeface.BOLD)
        "monospace" -> Typeface.create("monospace", Typeface.BOLD)
        "casual" -> Typeface.create("casual", Typeface.BOLD)
        else -> Typeface.DEFAULT_BOLD
    }

    /**
     * Renders status snapshot: battery percent in rounded rectangle + chips.
     */
    private fun renderStatus(snapshot: DimStatusSnapshot) {
        renderBattery(snapshot.batteryPercent, snapshot.isCharging)
        renderChips(snapshot.chips)
    }

    private fun renderBattery(percent: Int, isCharging: Boolean) {
        binding.dimBatteryLevelText.text = batteryTextFor(percent, isCharging)

        val color = ContextCompat.getColor(context, batteryColorResFor(percent, isCharging))
        binding.dimBatteryLevelText.setTextColor(color)
        binding.dimBatteryBox.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun renderChips(chips: List<DimStatusChip>) {
        val notifChips = chips.filter { it.isNotification }
        val statusChips = chips.filter { !it.isNotification }

        binding.dimNotificationsRow.isVisible = notifChips.isNotEmpty()
        populateChipContainer(binding.dimNotificationsRow, notifChips)

        populateChipContainer(binding.dimNetworkChipsContainer, statusChips)
    }

    private fun populateChipContainer(container: LinearLayout, chips: List<DimStatusChip>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val router = actionRouter
        chips.forEach { chip ->
            val chipBinding = ItemDimStatusChipBinding.inflate(inflater, container, false)
            val chipView = chipBinding.root
            val iconView = chipBinding.dimChipIcon
            val badgeView = chipBinding.dimChipBadge

            if (chip.iconResId != 0) {
                iconView.setImageResource(chip.iconResId)
                iconView.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            loadApplicationIcon(chip, iconView)
            if (chip.count > 1) {
                badgeView.isVisible = true
                badgeView.text = chip.count.toString()
            } else {
                badgeView.isVisible = false
            }
            chipView.contentDescription = chip.contentDescription
            if (router != null) {
                chipView.isClickable = true
                chipView.isFocusable = true
                chipView.setOnClickListener {
                    onDimExitRequested?.invoke()
                    Timber.d("S3366: notification chip tapped id=" + chip.id)
                    router.openChip(chip)
                }
            }
            container.addView(chipView)
        }
    }

    /**
     * S3366: swaps the static fallback for the application's own icon once the loader resolves it.
     * The fallback stays visible until then, so a slow lookup never blanks the chip; the tint and
     * padding are cleared with the real icon because a coloured application glyph needs the chip's
     * full square, exactly as on the launcher strip.
     */
    private fun loadApplicationIcon(chip: DimStatusChip, iconView: ImageView) {
        val packageName = chip.packageName ?: return
        val loader = iconLoader ?: return
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            val drawable = loader.load(packageName) ?: return@launch
            Timber.d("S3366: real icon applied for " + packageName)
            iconView.setImageDrawable(drawable)
            iconView.imageTintList = null
            iconView.setPadding(0, 0, 0, 0)
        }
    }

    /**
     * S3366: the clock block becomes a gesture surface only when a handler owns its semantics.
     * The detector decides direction through the handler (which shares the widget's resolver) and
     * re-applies the style after a handled swipe, so a dial change is visible immediately; the
     * double tap stays the wake-exit over the block, the exit gesture the owner kept as a guard.
     */
    private fun setupClockBlock() {
        val handler = interactionHandler ?: return
        val configuration = ViewConfiguration.get(context)
        binding.dimClockBlock.isClickable = true
        binding.dimClockBlock.isFocusable = true
        binding.dimClockBlock.setOnClickListener {
            handler.onClockTap { onDimExitRequested?.invoke() }
        }
        binding.dimClockBlock.setOnLongClickListener {
            handler.onClockLongPress { onDimExitRequested?.invoke() }
        }
        val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean =
                    handler.onClockTap { onDimExitRequested?.invoke() }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onDimExitRequested?.invoke()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    handler.onClockLongPress { onDimExitRequested?.invoke() }
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    val handled = handler.onFling(
                        distanceX = e2.x - (e1?.x ?: e2.x),
                        distanceY = e2.y - (e1?.y ?: e2.y),
                        velocityX = velocityX,
                        velocityY = velocityY,
                        touchSlop = configuration.scaledPagingTouchSlop.toFloat(),
                        minimumFlingVelocity = configuration.scaledMinimumFlingVelocity.toFloat(),
                    )
                    if (handled) styleProvider?.let { applyStyle(it.getStyle()) }
                    return handled
                }
            },
        )
        binding.dimClockBlock.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    /**
     * S3366: the battery box becomes a tap target only when a router can open the system
     * battery-usage screen behind it - the same flavor gate the notification chips take.
     */
    private fun setupBatteryBox() {
        val router = actionRouter ?: return
        binding.dimBatteryBox.isClickable = true
        binding.dimBatteryBox.isFocusable = true
        binding.dimBatteryBox.contentDescription =
            context.getString(R.string.app_launch_panel_os_battery)
        binding.dimBatteryBox.setOnClickListener {
            onDimExitRequested?.invoke()
            Timber.d("S3366: battery box tapped")
            router.openBatteryUsage()
        }
    }

    /**
     * Applies burn-in translation offset to prevent OLED burn-in.
     */
    fun applyBurnInShift(offsetX: Float, offsetY: Float) {
        binding.dimClockContentBlock.translationX = offsetX
        binding.dimClockContentBlock.translationY = offsetY
    }

    /**
     * Applies auto-fade opacity to the content block.
     */
    fun applyAutoFadeAlpha(alpha: Float) {
        binding.dimClockContentBlock.alpha = alpha
    }

    /**
     * One tick of the accepted extras: fade with idle time and drift the burn-in offset, the shift
     * advancing at its own period even when the cadence tick is faster.
     */
    private fun applyTickerEffects() {
        val now = System.currentTimeMillis()
        applyAutoFadeAlpha(ticker.computeAutoFadeAlpha(now))
        val (offsetXDp, offsetYDp) = ticker.updateBurnInOffset(now)
        val density = resources.displayMetrics.density
        applyBurnInShift(offsetXDp * density, offsetYDp * density)
    }

    /**
     * The host DimOverlayView owns every touch while dimmed, so it forwards them here; this view is
     * not clickable and would never see a tap otherwise.
     */
    fun onHostInteraction() {
        ticker.onUserActivity()
        applyAutoFadeAlpha(DimClockTicker.FULL_ALPHA)
    }

    companion object {
        private const val WEEKDAY_FIELD = "EEE "
        private const val BATTERY_WARNING_PERCENT = 30
        private const val BATTERY_CRITICAL_PERCENT = 15

        /**
         * Content padding as start, top, end, bottom (the [Insets] fields read relative). The top shift
         * clears the taller of the status bar and the cutout; the start edge takes the same shift on top
         * of its own safe inset, so the block sits as far in from the side as it sits down from the top.
         */
        internal fun contentPaddingFor(
            barsIgnoringVisibility: Insets,
            cutout: Insets,
            basePadding: Int,
            minimumShift: Int,
            isRtl: Boolean,
        ): Insets {
            val safe = Insets.max(barsIgnoringVisibility, cutout)
            val shift = maxOf(safe.top, minimumShift)
            val startSafe = if (isRtl) safe.right else safe.left
            val endSafe = if (isRtl) safe.left else safe.right
            return Insets.of(
                basePadding + startSafe + shift,
                basePadding + shift,
                basePadding + endSafe,
                basePadding + safe.bottom,
            )
        }

        /** Bolt plus the percent value while charging, the bare number otherwise. */
        internal fun batteryTextFor(percent: Int, isCharging: Boolean): String =
            if (isCharging) "⚡ $percent%" else "$percent%"

        /** The ladder: critical below 15, warning below 30, green while charging, white otherwise. */
        internal fun batteryColorResFor(percent: Int, isCharging: Boolean): Int = when {
            percent < BATTERY_CRITICAL_PERCENT -> R.color.error_color
            percent < BATTERY_WARNING_PERCENT -> R.color.warning_color
            isCharging -> R.color.success_color
            else -> R.color.white
        }
    }
}
