package com.sza.fastmediasorter.wear.data.wear

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.wear.domain.repository.WearExitReason
import com.sza.fastmediasorter.wear.domain.repository.WearHealthDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearThermalState
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * A battery extra the platform did not put in the intent. The framework's own default for these is -1,
 * which is indistinguishable from a genuine reading only for temperature, where -0.1 °C is possible on
 * a watch left outdoors - so the sentinel is checked against a value no real extra ever carries.
 */
private const val EXTRA_ABSENT = Int.MIN_VALUE

/** `BATTERY_PROPERTY_CHARGE_COUNTER` answers this when the gauge does not support the query. */
private const val CHARGE_COUNTER_UNSUPPORTED = Int.MIN_VALUE

/** The exit-reason history is asked for one record: the report shows the most recent death only. */
private const val EXIT_HISTORY_DEPTH = 1

/**
 * Reads the watch's own state off the platform.
 *
 * Every read is wrapped and degrades to null, on the same reasoning as
 * [AndroidWearSystemInfoDataSource]: this report is opened precisely when something already looks
 * wrong, so one refused fact must not cost the user the rest of it.
 *
 * Version guards are counted from **28**, the wear module's own floor - not from `app_v2`'s 26.
 */
class AndroidWearHealthDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : WearHealthDataSource {

    override val thermalState: WearThermalState?
        get() = read("thermal status") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager()?.currentThermalStatus?.let(::toThermalState)
            } else {
                null
            }
        }

    override val batteryTemperatureDeciCelsius: Int?
        get() = batteryExtra("battery temperature", BatteryManager.EXTRA_TEMPERATURE)

    override val batteryVoltageMilliVolts: Int?
        get() = batteryExtra("battery voltage", BatteryManager.EXTRA_VOLTAGE)

    override val batteryChargeCounterMicroAmpHours: Int?
        get() = read("charge counter") {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val counter = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (counter == null || counter == CHARGE_COUNTER_UNSUPPORTED) {
                null
            } else {
                counter
            }
        }

    override val uptimeMillis: Long?
        get() = read("uptime") { SystemClock.elapsedRealtime() }

    override val bootCount: Int?
        get() = read("boot count") {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }

    override val ignoringBatteryOptimizations: Boolean?
        get() = read("battery optimization exemption") {
            powerManager()?.isIgnoringBatteryOptimizations(context.packageName)
        }

    override val backgroundRestricted: Boolean?
        get() = read("background restriction") { activityManager()?.isBackgroundRestricted }

    override val lastExitReason: WearExitReason?
        get() = read("last exit reason") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activityManager()
                    ?.getHistoricalProcessExitReasons(context.packageName, 0, EXIT_HISTORY_DEPTH)
                    ?.firstOrNull()
                    ?.let { info -> toExitReason(info.reason) }
            } else {
                null
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun toThermalState(status: Int): WearThermalState? = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> WearThermalState.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> WearThermalState.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> WearThermalState.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> WearThermalState.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> WearThermalState.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> WearThermalState.EMERGENCY
        PowerManager.THERMAL_STATUS_SHUTDOWN -> WearThermalState.SHUTDOWN
        else -> null
    }

    /**
     * The platform's wider set collapses here, at the edge that reads it. Signal, permission change and
     * excessive resource use all mean "the system stopped it" to a watch owner, who can act on none of
     * the three differently.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun toExitReason(reason: Int): WearExitReason = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> WearExitReason.CRASH
        ApplicationExitInfo.REASON_CRASH_NATIVE -> WearExitReason.NATIVE_CRASH
        ApplicationExitInfo.REASON_ANR -> WearExitReason.NOT_RESPONDING
        ApplicationExitInfo.REASON_LOW_MEMORY -> WearExitReason.LOW_MEMORY
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> WearExitReason.SYSTEM
        ApplicationExitInfo.REASON_EXIT_SELF -> WearExitReason.SELF
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED -> WearExitReason.USER
        else -> WearExitReason.OTHER
    }

    /**
     * The sticky `ACTION_BATTERY_CHANGED` broadcast, which needs no receiver registration and no
     * permission: a null filter returns the last value the platform published.
     */
    private fun batteryExtra(what: String, name: String): Int? = read(what) {
        val status: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val value = status?.getIntExtra(name, EXTRA_ABSENT)
        if (value == null || value == EXTRA_ABSENT) {
            null
        } else {
            value
        }
    }

    private fun powerManager(): PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private fun activityManager(): ActivityManager? =
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    private fun <T> read(what: String, block: () -> T?): T? = runCatching(block)
        .onFailure { error -> Timber.w(error, "System info: %s unavailable", what) }
        .getOrNull()
}
