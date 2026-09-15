package com.sza.fastmediasorter.ui.flashlight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityFrontFlashlightBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.ui.dialog.ColorPickerDialog
import com.sza.fastmediasorter.ui.flashlight.helpers.FrontFlashlightBrightnessManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

/**
 * The front flashlight: the whole window becomes the light source (strategic S1796). Only the window's
 * own brightness is touched, never the device setting - see ADR-2, a program that raised the system
 * brightness would leave the device on maximum after it closed.
 *
 * Brightness is one of five steps (S2777). The visible strip and the vertical swipe drive the same
 * step index, so what is highlighted is always what the window is lit at.
 */
@AndroidEntryPoint
class FrontFlashlightActivity : BaseActivity<ActivityFrontFlashlightBinding>() {

    private val viewModel: FrontFlashlightViewModel by viewModels()

    @Inject
    lateinit var quantityFormatter: QuantityFormatter

    @Inject
    lateinit var unitSystemProvider: UnitSystemProvider

    private lateinit var gestureDetector: GestureDetector

    private val brightnessManager = FrontFlashlightBrightnessManager()

    private val brightnessCells: List<View> by lazy {
        listOf(
            binding.brightnessLevel1,
            binding.brightnessLevel2,
            binding.brightnessLevel3,
            binding.brightnessLevel4,
            binding.brightnessLevel5,
        )
    }

    // Travel carried between scroll events, so a slow drag still adds up to a whole step instead of
    // being rounded away event by event.
    private var scrollTravel: Float = 0f

    // Re-posted at each minute boundary rather than on a fixed tick, so the displayed minute changes
    // when it actually changes and the screen is not woken 60 times for one visible update.
    private val clockTick = Runnable { showTimeAndScheduleNext() }

    override fun getViewBinding(): ActivityFrontFlashlightBinding =
        ActivityFrontFlashlightBinding.inflate(layoutInflater)

    /**
     * The lamp may not go dark while it is the light in the room. The flag lives on this window, so
     * [BaseActivity] drops it with the window and the hold cannot outlive the program (§5.1.1).
     */
    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            brightnessManager.setLevel(it.getInt(STATE_BRIGHTNESS_LEVEL, brightnessManager.currentLevel))
        }
        // Window only: BaseActivity defers setupViews() to a post{}, so the strip does not exist yet.
        applyWindowBrightness()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_BRIGHTNESS_LEVEL, brightnessManager.currentLevel)
    }

    override fun setupViews() {
        Timber.d("flashlight opened, brightness level=${brightnessManager.currentLevel}")
        applyControlInsets()
        gestureDetector = GestureDetector(this, FlashlightGestures())
        binding.btnColor.setOnClickListener { openColorPicker() }
        bindBrightnessCells()
        refreshBrightnessSelection()
        supportFragmentManager.setFragmentResultListener(COLOR_REQUEST_KEY, this) { _, result ->
            val picked = result.getInt(ColorPickerDialog.RESULT_COLOR, currentGlowColor())
            Timber.d("glow colour picked %08X", picked)
            viewModel.setGlowColor(picked)
        }
        showTimeAndScheduleNext()
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.glowColor) { color ->
            binding.flashlightRoot.setBackgroundColor(color)
        }
        // The clock only redraws at a minute boundary, so a measurement system switched while the lamp
        // is on screen would otherwise keep the old clock length for up to a minute (S2795).
        collectOnLifecycle(unitSystemProvider.current) { renderClock() }
    }

    override fun onDestroy() {
        binding.tvClock.removeCallbacks(clockTick)
        super.onDestroy()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)

    /**
     * The screen is one large close target, so the same must be reachable without touch. A focused
     * control consumes the key first, which keeps the colour button usable from a D-pad (§3.2).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun applyControlInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.controlsOverlay) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            insets
        }
    }

    private fun bindBrightnessCells() {
        brightnessCells.forEachIndexed { index, cell ->
            cell.contentDescription =
                getString(R.string.front_flashlight_brightness_level, index + 1, brightnessManager.levelCount)
            cell.setOnClickListener {
                brightnessManager.setLevel(index)
                Timber.d("brightness step tapped -> ${brightnessManager.currentLevel}")
                applyBrightness()
            }
        }
    }

    private fun applyWindowBrightness() {
        window.attributes = window.attributes.apply { screenBrightness = brightnessManager.currentBrightness() }
    }

    private fun refreshBrightnessSelection() {
        brightnessCells.forEachIndexed { index, cell ->
            cell.isSelected = index == brightnessManager.currentLevel
        }
    }

    private fun applyBrightness() {
        applyWindowBrightness()
        refreshBrightnessSelection()
    }

    private fun currentGlowColor(): Int = viewModel.glowColor.value

    private fun openColorPicker() {
        ColorPickerDialog
            .newInstance(
                initialColor = currentGlowColor(),
                subjectId = SUBJECT_GLOW,
                requestKey = COLOR_REQUEST_KEY,
            )
            .show(supportFragmentManager, ColorPickerDialog.TAG)
    }

    /**
     * The app's measurement system decides the clock length, not the device's 12/24 switch (S2795):
     * the lamp's clock must read the same as every other time in the program.
     */
    private fun renderClock() {
        binding.tvClock.text = quantityFormatter.format(
            Quantity.Instant(System.currentTimeMillis()),
            unitSystemProvider.value,
        )
    }

    private fun showTimeAndScheduleNext() {
        renderClock()
        binding.tvClock.postDelayed(clockTick, millisUntilNextMinute())
    }

    private fun millisUntilNextMinute(): Long =
        MINUTE_MS - System.currentTimeMillis() % MINUTE_MS

    private inner class FlashlightGestures : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            scrollTravel = 0f
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            finish()
            return true
        }

        /**
         * A full sweep of the window height still spans the whole range, now as whole steps rather
         * than a continuous value. Horizontal drags are ignored rather than diluted into the value.
         */
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            val height = binding.flashlightRoot.height
            if (height <= 0 || abs(distanceY) <= abs(distanceX)) return false
            scrollTravel += distanceY
            val stepTravel = height.toFloat() / (brightnessManager.levelCount - 1)
            val steps = (scrollTravel / stepTravel).toInt()
            if (steps != 0) {
                scrollTravel -= steps * stepTravel
                brightnessManager.shiftLevels(steps)
                Timber.d("brightness gesture -> level ${brightnessManager.currentLevel}")
                applyBrightness()
            }
            return true
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, FrontFlashlightActivity::class.java)

        private const val COLOR_REQUEST_KEY = "front_flashlight_color_result"
        private const val SUBJECT_GLOW = "front_flashlight_glow"
        private const val STATE_BRIGHTNESS_LEVEL = "state_brightness_level"
        private const val MINUTE_MS = 60_000L
    }
}
