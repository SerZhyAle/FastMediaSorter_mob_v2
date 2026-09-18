package com.sza.fastmediasorter.wear.domain.power

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3265: the watch's own power policy, in one place.
 *
 * A microphone foreground service is not enough on this hardware. During an undisturbed doze window a
 * Samsung background-management daemon force-stopped the whole package while a broadcast was on air and
 * serving bytes, and a force-stopped package is never restarted by the platform - no `onStartCommand`
 * return value defends against that kill class. The two defences that do are a CPU hold for the length
 * of the session and a battery-optimization exemption the owner grants once, and both need platform
 * `PowerManager` calls that neither a `Service` nor a `ViewModel` should carry inline.
 */
@Singleton
class WearPowerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val powerManager: PowerManager?
        get() = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /**
     * True when the platform has been told to leave this package alone in doze. Read fresh on every
     * call: the owner grants it in a system screen this process never sees the result of.
     */
    fun isIgnoringBatteryOptimizations(): Boolean =
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    /**
     * The direct request dialog first, the app's battery settings page second. Wear OS firmwares differ
     * on which of the two exists, so the caller walks the list rather than trusting either one; the
     * first intent that starts is the only one used.
     */
    fun batteryOptimizationIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", context.packageName, null)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
    )

    /**
     * A partial lock: the CPU keeps running, the display is left alone. The timeout is the backstop for
     * the one path that cannot be covered by a release call - the process dying while a session is open -
     * and is set far beyond any session the owner would hold deliberately.
     */
    fun acquirePartialWakeLock(tag: String): PowerManager.WakeLock? {
        val lock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        if (lock == null) {
            Timber.w("No PowerManager on this device; the session runs without a CPU hold")
            return null
        }
        lock.setReferenceCounted(false)
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        return lock
    }

    /** Safe to call on a lock the timeout already expired: `isHeld` is what decides. */
    fun releaseWakeLock(lock: PowerManager.WakeLock?) {
        if (lock != null && lock.isHeld) {
            lock.release()
        }
    }

    companion object {
        /** Named after the package so the lock is identifiable in `dumpsys power`. */
        const val SESSION_WAKE_LOCK_TAG = "FastMediaSorter:WearVoiceRecordingWakeLock"

        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L
    }
}
