package com.sza.fastmediasorter.ui.common.widget.dimclock

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

    private var observationJob: Job? = null
    private var currentUnitSystem: UnitSystem = UnitSystem.METRIC

    init {
        isClickable = false
        isFocusable = false
        contentDescription = context.getString(R.string.dim_clock_status_cd)

        // Safe bounds handling (CLAUDE.md Rule 17)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val basePadding = resources.getDimensionPixelSize(R.dimen.dim_clock_padding)
            binding.dimClockContentBlock.setPadding(
                basePadding + safeInsets.left,
                basePadding + safeInsets.top,
                basePadding + safeInsets.right,
                basePadding + safeInsets.bottom
            )
            insets
        }
    }

    /**
     * Injects dependencies and starts observing style and status.
     */
    fun bind(
        styleProvider: DimClockStyleProvider,
        statusProvider: DimStatusContentProvider,
        unitSystemProvider: UnitSystemProvider? = null,
    ) {
        this.styleProvider = styleProvider
        this.statusProvider = statusProvider
        this.unitSystemProvider = unitSystemProvider
        applyStyle(styleProvider.getStyle())
        startObserving()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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
        }
    }

    /**
     * Applies visual styling from DimClockStyle (typeface, dial color, seconds visibility).
     */
    fun applyStyle(style: DimClockStyle) {
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
        val batteryText = if (isCharging) "⚡ %" else "%"
        binding.dimBatteryLevelText.text = batteryText

        val color = when {
            percent < BATTERY_CRITICAL_PERCENT -> ContextCompat.getColor(context, R.color.error_color)
            percent < BATTERY_WARNING_PERCENT -> ContextCompat.getColor(context, R.color.warning_color)
            isCharging -> ContextCompat.getColor(context, R.color.success_color)
            else -> ContextCompat.getColor(context, R.color.white)
        }

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
        chips.forEach { chip ->
            val chipBinding = ItemDimStatusChipBinding.inflate(inflater, container, false)
            val chipView = chipBinding.root
            val iconView = chipBinding.dimChipIcon
            val badgeView = chipBinding.dimChipBadge

            if (chip.iconResId != 0) {
                iconView.setImageResource(chip.iconResId)
                iconView.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            if (chip.count > 1) {
                badgeView.isVisible = true
                badgeView.text = chip.count.toString()
            } else {
                badgeView.isVisible = false
            }
            chipView.contentDescription = chip.contentDescription
            container.addView(chipView)
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

    companion object {
        private const val WEEKDAY_FIELD = "EEE "
        private const val BATTERY_WARNING_PERCENT = 30
        private const val BATTERY_CRITICAL_PERCENT = 15
    }
}
