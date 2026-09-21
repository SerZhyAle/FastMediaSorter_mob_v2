package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber

/**
 * S1906: opening the clock app is now the tap of two gadgets - the local clock and the world clock -
 * so the "this device may have no clock app" guard is stated once instead of being copied.
 *
 * A missing clock app is a silent no-op rather than a crash: this runs on the home screen, where an
 * unhandled intent takes the whole desktop down with it.
 *
 * S3366: the dim clock's tap and long press run through here too, and its ADR-2 ordering is why the
 * opens take a [beforeStart] prelude - the dim overlay must be dismissed after the target is known
 * to exist and before the activity starts, which no gadget caller needs and none of them passes.
 */
internal fun openSystemClock(context: Context, beforeStart: (() -> Unit)? = null) {
    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (context.packageManager.resolveActivityCompat(intent) == null) {
        Timber.i("Launcher clock gadget: no system alarm app to open")
        return
    }
    beforeStart?.invoke()
    runCatching { context.startActivity(intent) }
        .onFailure { Timber.w(it, "Launcher clock gadget: system alarm app refused to open") }
}

/** S3366: the widget's long-press calendar open, moved here so the dim clock shares the guard. */
internal fun openCalendarAtNow(context: Context, beforeStart: (() -> Unit)? = null) {
    val uri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .also { ContentUris.appendId(it, System.currentTimeMillis()) }
        .build()
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (context.packageManager.resolveActivityCompat(intent) == null) {
        Timber.i("Launcher clock gadget: no calendar app to open")
        return
    }
    beforeStart?.invoke()
    runCatching { context.startActivity(intent) }
        .onFailure { Timber.w(it, "Launcher clock gadget: calendar app refused to open") }
}
