package com.sza.fastmediasorter.wear.ui.common

import android.app.Activity
import android.app.ActivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import timber.log.Timber

/**
 * Holds the system shade shut while [enabled], and gives it back on the way out (S2812).
 *
 * Lock task mode is the only mechanism left on a modern watch: an application overlay may not draw over
 * the status bar, and Wear OS 4 removed the system UI that grants the overlay permission at all. A task in
 * lock task mode has no shade to pull - the status bar carries nothing.
 *
 * Whether this build may ask for it is a flavor answer carried by `WearRestrictedCapabilities`, never a
 * flavor check here.
 */
@Composable
fun SystemShadeLockEffect(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        // Only a lock this effect started is released again: a mode someone else put the task into is
        // not ours to end, and skipping the release of our own would leave the watch pinned after the
        // screen closes - the worse of the two failures.
        val locked = if (enabled) activity?.takeIf(SystemShadeLock::acquire) else null
        onDispose { locked?.let(SystemShadeLock::release) }
    }
}

/**
 * The two one-way calls onto the hosting Activity, each refusing to act when the task is already in a
 * lock task state - started by an ordinary app this is screen pinning, so the state to expect back is
 * `LOCK_TASK_MODE_PINNED` rather than the device-owner `LOCK_TASK_MODE_LOCKED`.
 */
private object SystemShadeLock {

    /** Returns whether this call is the one that put the task into lock task mode. */
    fun acquire(activity: Activity): Boolean {
        if (lockState(activity) != ActivityManager.LOCK_TASK_MODE_NONE) return false
        return try {
            activity.startLockTask()
            true
        } catch (e: IllegalStateException) {
            // The screen keeps working without the lock: failing to shut the shade is not a reason to
            // fail to light, which is the whole point of the program.
            Timber.w(e, "System shade lock refused by the platform")
            false
        }
    }

    fun release(activity: Activity) {
        if (lockState(activity) == ActivityManager.LOCK_TASK_MODE_NONE) return
        try {
            activity.stopLockTask()
        } catch (e: IllegalStateException) {
            Timber.w(e, "System shade unlock refused by the platform")
        }
    }

    /**
     * `LOCK_TASK_MODE_NONE` when the service is unreachable: an unknown state must read as "not locked",
     * so [acquire] may try and [release] does not fire on a guess.
     */
    private fun lockState(activity: Activity): Int {
        val manager = activity.getSystemService(ActivityManager::class.java)
        return manager?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
    }
}
