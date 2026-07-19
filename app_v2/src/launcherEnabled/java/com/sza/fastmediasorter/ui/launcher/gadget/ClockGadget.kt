package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherClockBinding
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * S0404: time and date on the desktop. Weather is deliberately absent - S0426 extends this gadget
 * through the contract (strategic §6.7), so there is no placeholder to mislead the user meanwhile.
 */
class ClockGadget @Inject constructor() : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_CLOCK
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_clock
    override val iconRes: Int = R.drawable.ic_schedule
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        ClockGadgetView(container.context)
}

/**
 * No lifecycle work at all: both [android.widget.TextClock]s drive themselves. The base class is still
 * the right parent - it is what makes "a gadget owns its own teardown" true by construction rather
 * than by review.
 */
private class ClockGadgetView(context: Context) : LauncherGadgetView(context) {

    init {
        val binding = GadgetLauncherClockBinding.inflate(LayoutInflater.from(context), this)
        // The date line has no fixed shape across locales - ask the platform for this locale's pattern
        // instead of hardcoding one that reads wrong outside en-US.
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), DATE_SKELETON)
        binding.gadgetClockDate.format12Hour = datePattern
        binding.gadgetClockDate.format24Hour = datePattern
        setOnClickListener { openSystemClock(context) }
    }

    /**
     * Tapping a clock should open the clock app, but there is no guarantee one exists - a bare
     * startActivity would crash the home screen on a device without it.
     */
    private fun openSystemClock(context: Context) {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivityCompat(intent) == null) {
            Timber.i("Launcher clock gadget: no system alarm app to open")
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Launcher clock gadget: system alarm app refused to open") }
    }

    private companion object {
        /** Weekday + day + short month; the platform orders them per locale. */
        const val DATE_SKELETON = "EEEdMMM"
    }
}
