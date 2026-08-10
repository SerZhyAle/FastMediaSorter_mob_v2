package com.sza.fastmediasorter.ui.networkmonitor.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * S1433: opens the first of [intents] that this device actually has, and reports whether any of them did.
 *
 * Tried in order rather than probed with the PackageManager first. Resolution is binder IPC and this runs
 * inside a tap handler, and more importantly an absent surface is an expected outcome here rather than a
 * fault: `Settings.Panel.*` exists only from API 29, so on an older device the first candidate is simply
 * missing and the full settings screen behind it is the correct answer, not a failure worth reporting.
 */
internal fun Context.startFirstAvailableSystemSurface(intents: List<Intent>): Boolean {
    val opened = intents.any { intent -> tryStartSystemSurface(intent) }
    if (!opened) {
        Timber.i("No system network surface resolved on this device")
    }
    return opened
}

private fun Context.tryStartSystemSurface(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (missing: ActivityNotFoundException) {
    Timber.i(missing, "System surface %s is not present on this device", intent.action)
    false
}
