package com.sza.fastmediasorter.ui.flashlight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.core.screencapture.gesture.DeviceActionHandler
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityWaterFlashlightBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

/**
 * The water flashlight: torch and window lit together behind a screen a wet finger cannot close
 * (strategic S2516).
 *
 * Its neighbour [FrontFlashlightActivity] is closed by a tap and dimmed by a drag. Here a touch must
 * reach nothing at all, which is why this is a second program rather than a mode of that one - the
 * two answer the same gesture in opposite ways (ADR-1).
 */
@AndroidEntryPoint
class WaterFlashlightActivity : BaseActivity<ActivityWaterFlashlightBinding>() {

    @Inject
    lateinit var deviceActionHandler: DeviceActionHandler

    // Re-posted at each minute boundary rather than on a fixed tick, so the displayed minute changes
    // when it actually changes and the screen is not woken 60 times for one visible update.
    private val clockTick = Runnable { showTimeAndScheduleNext() }

    override fun getViewBinding(): ActivityWaterFlashlightBinding =
        ActivityWaterFlashlightBinding.inflate(layoutInflater)

    /**
     * The lamp may not go dark while it is the light in the water. The flag lives on this window, so
     * [BaseActivity] drops it with the window and the hold cannot outlive the program.
     */
    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only this window's brightness, never the device setting - closing the program must leave the
        // phone exactly as bright as it was, the same rule the front flashlight follows.
        window.attributes = window.attributes.apply { screenBrightness = MAX_BRIGHTNESS }
    }

    override fun setupViews() {
        showTimeAndScheduleNext()
    }

    override fun observeData() = Unit

    /**
     * The torch follows the foreground rather than the window's destruction: whichever way the program
     * is left - a key, a system gesture, the power button - it must not leave a light burning in a
     * pocket (§5.1.6).
     */
    override fun onStart() {
        super.onStart()
        Timber.d("S2516: water flashlight entered foreground, requesting torch on")
        deviceActionHandler.setTorch(this, true)
    }

    override fun onStop() {
        Timber.d("S2516: water flashlight left foreground, requesting torch off")
        deviceActionHandler.setTorch(this, false)
        super.onStop()
    }

    override fun onDestroy() {
        binding.tvClock.removeCallbacks(clockTick)
        super.onDestroy()
    }

    /** Water on the glass fires touches of its own, so every one of them ends here and means nothing. */
    override fun onTouchEvent(event: MotionEvent): Boolean = true

    override fun onGenericMotionEvent(event: MotionEvent): Boolean = true

    /**
     * Taken at dispatch rather than in `onKeyDown`: [BaseActivity] routes the volume keys through the
     * shared TV key router before a screen would see them, so a later hook would change the volume
     * instead of leaving. `KEYCODE_BACK` is absent on purpose - under gesture navigation it arrives
     * from a swipe across the glass and cannot be told apart from the touch this screen ignores
     * (ADR-2). The down event is swallowed too, so the exit press does not also move the volume.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Timber.d("S2516: water flashlight saw key %d action %d", event.keyCode, event.action)
        if (event.keyCode !in EXIT_KEYS) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP) {
            Timber.d("water flashlight left by hardware key %d", event.keyCode)
            finish()
        }
        return true
    }

    private fun showTimeAndScheduleNext() {
        binding.tvClock.text = DateFormat.getTimeFormat(this).format(Date())
        binding.tvClock.postDelayed(clockTick, millisUntilNextMinute())
    }

    private fun millisUntilNextMinute(): Long =
        MINUTE_MS - System.currentTimeMillis() % MINUTE_MS

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, WaterFlashlightActivity::class.java)

        private const val MAX_BRIGHTNESS = 1.0f
        private const val MINUTE_MS = 60_000L

        /**
         * Keys the platform delivers only from hardware. Power is not among them at all - the system
         * consumes it before any app sees it - so it ends the program the other way, by taking it off
         * the foreground.
         */
        private val EXIT_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        )
    }
}
