package com.sza.fastmediasorter.wear.domain.repository

private const val BATTERY_TEMPERATURE_DECI_CELSIUS = 312
private const val BATTERY_VOLTAGE_MILLI_VOLTS = 3987
private const val CHARGE_COUNTER_MICRO_AMP_HOURS = 198_000
private const val UPTIME_MILLIS = 4_530_000L
private const val BOOT_COUNT = 41

/**
 * Mutable rather than constructed, on the same reasoning as [FakeWearSystemInfoDataSource]: a
 * constructor taking nine facts is unreadable at the call site and past detekt's parameter ceiling, and
 * each test names only the fact it is about.
 */
class FakeWearHealthDataSource : WearHealthDataSource {
    override var thermalState: WearThermalState? = WearThermalState.NONE
    override var batteryTemperatureDeciCelsius: Int? = BATTERY_TEMPERATURE_DECI_CELSIUS
    override var batteryVoltageMilliVolts: Int? = BATTERY_VOLTAGE_MILLI_VOLTS
    override var batteryChargeCounterMicroAmpHours: Int? = CHARGE_COUNTER_MICRO_AMP_HOURS
    override var uptimeMillis: Long? = UPTIME_MILLIS
    override var bootCount: Int? = BOOT_COUNT
    override var ignoringBatteryOptimizations: Boolean? = false
    override var backgroundRestricted: Boolean? = false
    override var lastExitReason: WearExitReason? = WearExitReason.SELF

    /** A watch that refuses every one of these questions - the case the emptiness reason exists for. */
    fun blind(): FakeWearHealthDataSource = apply {
        thermalState = null
        batteryTemperatureDeciCelsius = null
        batteryVoltageMilliVolts = null
        batteryChargeCounterMicroAmpHours = null
        uptimeMillis = null
        bootCount = null
        ignoringBatteryOptimizations = null
        backgroundRestricted = null
        lastExitReason = null
    }
}
