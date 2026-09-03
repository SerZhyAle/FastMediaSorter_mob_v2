package com.sza.fastmediasorter.wear.data.wear

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.wear.domain.repository.WearHardwareDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearSensorDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

private const val BAND_2_4_GHZ = "2.4 GHz"
private const val BAND_5_GHZ = "5 GHz"
private const val BAND_6_GHZ = "6 GHz"

/**
 * Reads what this watch is made of.
 *
 * Every read is wrapped and degrades to null, and every API above the module's floor of **28** carries
 * its own guard. Sensors are mapped to a domain descriptor here rather than handed upwards:
 * `android.hardware.Sensor` cannot be constructed on the plain JVM the unit suite runs on, so a
 * contributor holding one could not be tested at all.
 *
 * No permission is added by any of this. Describing a sensor is not reading one, and the Wi-Fi
 * capability queries run under `ACCESS_WIFI_STATE`, which this module's manifest has declared for
 * years.
 */
class AndroidWearHardwareDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : WearHardwareDataSource {

    override val sensors: List<WearSensorDescriptor>?
        get() = read("sensors") {
            sensorManager()?.getSensorList(Sensor.TYPE_ALL)?.map { sensor ->
                WearSensorDescriptor(
                    name = sensor.name,
                    vendor = sensor.vendor,
                    powerMilliAmps = sensor.power,
                    resolution = sensor.resolution
                )
            }
        }

    override val supportedAbis: List<String>?
        get() = read("abi set") { Build.SUPPORTED_ABIS?.toList() }

    override val socManufacturer: String?
        get() = read("soc manufacturer") { soc { Build.SOC_MANUFACTURER } }

    override val socModel: String?
        get() = read("soc model") { soc { Build.SOC_MODEL } }

    override val cpuCoreCount: Int?
        get() = read("cpu cores") { Runtime.getRuntime().availableProcessors() }

    override val lowRamDevice: Boolean?
        get() = read("low ram") { activityManager()?.isLowRamDevice }

    override val supportedWifiBands: List<String>?
        get() = read("wifi bands") { wifiManager()?.let(::bandsOf) }

    override val supportedWifiStandards: List<String>?
        get() = read("wifi standards") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wifiManager()?.let(::standardsOf)
            } else {
                null
            }
        }

    /**
     * 2.4 GHz is listed unconditionally: every Wi-Fi radio Android runs on supports it, and the
     * platform offers no query for it - a band list that silently omitted the one band the watch
     * certainly has would read as a defect.
     */
    private fun bandsOf(wifi: WifiManager): List<String> = buildList {
        add(BAND_2_4_GHZ)
        if (wifi.is5GHzBandSupported) {
            add(BAND_5_GHZ)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi.is6GHzBandSupported) {
            add(BAND_6_GHZ)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun standardsOf(wifi: WifiManager): List<String> = buildList {
        if (wifi.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11N)) {
            add("Wi-Fi 4")
        }
        if (wifi.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC)) {
            add("Wi-Fi 5")
        }
        if (wifi.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX)) {
            add("Wi-Fi 6")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            wifi.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11BE)
        ) {
            add("Wi-Fi 7")
        }
    }

    /** `Build.UNKNOWN` is the platform's way of saying it will not tell, so it is treated as absent. */
    private fun soc(value: () -> String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            value().takeUnless { answer -> answer.isBlank() || answer == Build.UNKNOWN }
        } else {
            null
        }

    private fun sensorManager(): SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private fun activityManager(): ActivityManager? =
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    private fun wifiManager(): WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private fun <T> read(what: String, block: () -> T?): T? = runCatching(block)
        .onFailure { error -> Timber.w(error, "System info: %s unavailable", what) }
        .getOrNull()
}
