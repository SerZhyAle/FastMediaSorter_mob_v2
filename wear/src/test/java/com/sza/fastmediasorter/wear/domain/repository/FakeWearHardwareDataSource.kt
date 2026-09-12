package com.sza.fastmediasorter.wear.domain.repository

private const val ACCELEROMETER_POWER_MA = 0.25f
private const val ACCELEROMETER_RESOLUTION = 0.0012f
private const val HEART_RATE_POWER_MA = 6.5f
private const val HEART_RATE_RESOLUTION = 1.0f
private const val CORE_COUNT = 4

/**
 * Mutable rather than constructed, on the same reasoning as the other two fakes: each test names only
 * the fact it is about.
 */
class FakeWearHardwareDataSource : WearHardwareDataSource {
    override var sensors: List<WearSensorDescriptor>? = listOf(
        WearSensorDescriptor("Accelerometer", "STM", ACCELEROMETER_POWER_MA, ACCELEROMETER_RESOLUTION),
        WearSensorDescriptor("Heart rate", "Samsung", HEART_RATE_POWER_MA, HEART_RATE_RESOLUTION)
    )
    override var supportedAbis: List<String>? = listOf("arm64-v8a", "armeabi-v7a")
    override var socManufacturer: String? = "Samsung"
    override var socModel: String? = "Exynos W1000"
    override var cpuCoreCount: Int? = CORE_COUNT
    override var lowRamDevice: Boolean? = false
    override var supportedWifiBands: List<String>? = listOf("2.4 GHz", "5 GHz")
    override var supportedWifiStandards: List<String>? = listOf("Wi-Fi 4", "Wi-Fi 5")
}
