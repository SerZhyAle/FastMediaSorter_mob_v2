package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber

/**
 * S1906: opening the clock app is now the tap of two gadgets - the local clock and the world clock -
 * so the "this device may have no clock app" guard is stated once instead of being copied.
 *
 * A missing clock app is a silent no-op rather than a crash: this runs on the home screen, where an
 * unhandled intent takes the whole desktop down with it.
 */
internal fun openSystemClock(context: Context) {
    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (context.packageManager.resolveActivityCompat(intent) == null) {
        Timber.i("Launcher clock gadget: no system alarm app to open")
        return
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Timber.w(it, "Launcher clock gadget: system alarm app refused to open") }
}
