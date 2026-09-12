package com.sza.fastmediasorter.wear.domain.repository

/**
 * How hot the watch says it is, as a state rather than as the platform's integer.
 *
 * Named here rather than mapped in the report, so that `PowerManager.THERMAL_STATUS_*` - which exists
 * only from API 29, above this module's floor of 28 - stays behind the version guard in the data source
 * and never reaches domain code.
 */
enum class WearThermalState { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }

/**
 * Why the app's process died last time - the one fact separating "the system reclaimed it" from "it
 * crashed", which the user otherwise experiences identically.
 *
 * The platform's set is wider than this. It is collapsed here, at the edge that reads it, because the
 * distinctions it drops are ones no watch owner can act on: a process killed by signal, by a permission
 * change or for using too much of something was stopped by the system either way.
 */
enum class WearExitReason { CRASH, NATIVE_CRASH, NOT_RESPONDING, LOW_MEMORY, SYSTEM, SELF, USER, OTHER }

/**
 * The state facts a user opens the system report to find out - why the watch or the app is behaving
 * oddly right now.
 *
 * None of these appears on any settings screen of the watch, which is the criterion the whole report is
 * selected by (S2165 §2 goal 1), and none costs a permission: thermal state, battery detail, the app's
 * own background standing and the reason its process last died are all readable by an app about itself.
 *
 * Every member is nullable, and null means the watch would not answer - not zero, and not "false". A
 * fact this device cannot produce leaves its line out of the report rather than printing a placeholder
 * the user cannot tell from a reading.
 */
interface WearHealthDataSource {

    val thermalState: WearThermalState?

    val batteryTemperatureDeciCelsius: Int?

    val batteryVoltageMilliVolts: Int?

    /** Remaining charge in µAh - the absolute figure the percentage on the watch face is derived from. */
    val batteryChargeCounterMicroAmpHours: Int?

    val uptimeMillis: Long?

    /** How many times this watch has booted in its life. */
    val bootCount: Int?

    val ignoringBatteryOptimizations: Boolean?

    val backgroundRestricted: Boolean?

    val lastExitReason: WearExitReason?
}
