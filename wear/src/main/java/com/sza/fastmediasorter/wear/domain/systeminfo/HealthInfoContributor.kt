package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.repository.WearExitReason
import com.sza.fastmediasorter.wear.domain.repository.WearHealthDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearThermalState
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val DECI = 10.0
private const val MICRO_PER_MILLI = 1000

/**
 * The section the report is opened for: why this watch, or this app on it, is behaving oddly.
 *
 * Nothing here appears on a settings screen of the watch, which is the whole selection criterion
 * (S2165 §2 goal 1), and every state arrives as a domain value rather than as a platform integer, so
 * this class imports nothing from `android.*` and stays testable on the plain JVM the module's unit
 * suite runs on.
 */
class HealthInfoContributor @Inject constructor(
    private val dataSource: WearHealthDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.HEALTH

    override suspend fun sections(): List<WearSystemInfoSection> = listOf(
        section(
            titleRes = R.string.system_info_section_health,
            fields = listOfNotNull(
                thermalField(),
                text(R.string.system_info_health_battery_temp, batteryTemperature()),
                text(R.string.system_info_health_battery_voltage, batteryVoltage()),
                text(R.string.system_info_health_battery_charge, chargeLeft()),
                text(R.string.system_info_health_uptime, uptime()),
                text(R.string.system_info_health_boot_count, dataSource.bootCount?.toString()),
                yesNo(R.string.system_info_health_doze_exempt, dataSource.ignoringBatteryOptimizations),
                backgroundStanding(),
                lastExit()
            ),
            emptyReasonRes = R.string.system_info_empty_unreadable
        )
    )

    private fun thermalField(): WearSystemInfoField? = dataSource.thermalState?.let { state ->
        label(R.string.system_info_health_thermal, thermalLabel(state))
    }

    private fun thermalLabel(state: WearThermalState): Int = when (state) {
        WearThermalState.NONE -> R.string.system_info_thermal_none
        WearThermalState.LIGHT -> R.string.system_info_thermal_light
        WearThermalState.MODERATE -> R.string.system_info_thermal_moderate
        WearThermalState.SEVERE -> R.string.system_info_thermal_severe
        WearThermalState.CRITICAL -> R.string.system_info_thermal_critical
        WearThermalState.EMERGENCY -> R.string.system_info_thermal_emergency
        WearThermalState.SHUTDOWN -> R.string.system_info_thermal_shutdown
    }

    private fun batteryTemperature(): String? = dataSource.batteryTemperatureDeciCelsius
        ?.let { deci -> String.format(Locale.US, "%.1f C", deci / DECI) }

    private fun batteryVoltage(): String? = dataSource.batteryVoltageMilliVolts
        ?.let { millivolts -> String.format(Locale.US, "%d mV", millivolts) }

    private fun chargeLeft(): String? = dataSource.batteryChargeCounterMicroAmpHours
        ?.let { microAmpHours -> String.format(Locale.US, "%d mAh", microAmpHours / MICRO_PER_MILLI) }

    /**
     * Days are spelled out rather than folded into hours: a watch up for nine days and a watch that
     * rebooted an hour ago answer different questions, and "216h" says the first one badly.
     */
    private fun uptime(): String? = dataSource.uptimeMillis?.let { millis ->
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % TimeUnit.DAYS.toHours(1)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
        String.format(Locale.US, "%dd %02dh %02dm", days, hours, minutes)
    }

    private fun yesNo(labelRes: Int, value: Boolean?): WearSystemInfoField? = value?.let { answer ->
        label(labelRes, if (answer) R.string.system_info_yes else R.string.system_info_no)
    }

    private fun backgroundStanding(): WearSystemInfoField? =
        dataSource.backgroundRestricted?.let { restricted ->
            val answer = if (restricted) {
                R.string.system_info_health_background_limited
            } else {
                R.string.system_info_health_background_normal
            }
            label(R.string.system_info_health_background, answer)
        }

    private fun lastExit(): WearSystemInfoField? = dataSource.lastExitReason?.let { reason ->
        label(R.string.system_info_health_last_exit, exitLabel(reason))
    }

    private fun exitLabel(reason: WearExitReason): Int = when (reason) {
        WearExitReason.CRASH -> R.string.system_info_exit_crash
        WearExitReason.NATIVE_CRASH -> R.string.system_info_exit_native_crash
        WearExitReason.NOT_RESPONDING -> R.string.system_info_exit_anr
        WearExitReason.LOW_MEMORY -> R.string.system_info_exit_low_memory
        WearExitReason.SYSTEM -> R.string.system_info_exit_system
        WearExitReason.SELF -> R.string.system_info_exit_self
        WearExitReason.USER -> R.string.system_info_exit_user
        WearExitReason.OTHER -> R.string.system_info_exit_other
    }
}
