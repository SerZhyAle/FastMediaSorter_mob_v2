package com.sza.fastmediasorter.ui.flashlight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityFrontFlashlightBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.dialog.ColorPickerDialog
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Date
import kotlin.math.abs

/**
 * The front flashlight: the whole window becomes the light source (strategic S1796). Only the window's
 * own brightness is touched, never the device setting - see ADR-2, a program that raised the system
 * brightness would leave the device on maximum after it closed.
 */
@AndroidEntryPoint
class FrontFlashlightActivity : BaseActivity<ActivityFrontFlashlightBinding>() {

    private val viewModel: FrontFlashlightViewModel by viewModels()

    private lateinit var gestureDetector: GestureDetector

    private var brightness: Float = MAX_BRIGHTNESS

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
        brightness = savedInstanceState?.getFloat(STATE_BRIGHTNESS, MAX_BRIGHTNESS) ?: MAX_BRIGHTNESS
        applyBrightness()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat(STATE_BRIGHTNESS, brightness)
    }

    override fun setupViews() {
        Timber.d("S1796: flashlight entry - colour=%08X, restored brightness=%s", currentGlowColor(), brightness)
        Timber.d("flashlight opened, brightness=$brightness")
        applyControlInsets()
        gestureDetector = GestureDetector(this, FlashlightGestures())
        binding.btnColor.setOnClickListener { openColorPicker() }
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

    private fun applyBrightness() {
        window.attributes = window.attributes.apply { screenBrightness = brightness }
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

    private fun showTimeAndScheduleNext() {
        binding.tvClock.text = DateFormat.getTimeFormat(this).format(Date())
        binding.tvClock.postDelayed(clockTick, millisUntilNextMinute())
    }

    private fun millisUntilNextMinute(): Long =
        MINUTE_MS - System.currentTimeMillis() % MINUTE_MS

    private inner class FlashlightGestures : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            finish()
            return true
        }

        /**
         * A full sweep of the window height covers the whole range, so the control is the same
         * distance on any screen. Horizontal drags are ignored rather than diluted into the value.
         */
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            val height = binding.flashlightRoot.height
            if (height <= 0 || abs(distanceY) <= abs(distanceX)) return false
            brightness = (brightness + distanceY / height).coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
            Timber.d("brightness gesture -> $brightness")
            applyBrightness()
            return true
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, FrontFlashlightActivity::class.java)

        private const val COLOR_REQUEST_KEY = "front_flashlight_color_result"
        private const val SUBJECT_GLOW = "front_flashlight_glow"
        private const val STATE_BRIGHTNESS = "state_brightness"
        private const val MAX_BRIGHTNESS = 1.0f

        // Never fully dark: a black screen reads as a crash, not as the dimmest setting of a lamp.
        private const val MIN_BRIGHTNESS = 0.05f
        private const val MINUTE_MS = 60_000L
    }
}
