package com.sza.fastmediasorter.wear.domain.repository

/**
 * One sensor this watch physically carries, described rather than read.
 *
 * @param type what the sensor is FOR, in the platform's own vocabulary and stripped of its
 * `android.sensor.` prefix - `heart_rate`, `accelerometer`. Carried since S3108 because [name] and
 * [vendor] are a part number and a manufacturer, so a list built from them alone answers how many
 * sensors the watch has and never which. The platform's string is taken instead of a translated
 * table of `Sensor.TYPE_*`: a vendor sensor has no constant to translate and still answers here.
 * Empty when the platform would not say.
 */
data class WearSensorDescriptor(
    val type: String,
    val name: String,
    val vendor: String,
    val powerMilliAmps: Float,
    val resolution: Float
)

/**
 * What this watch is made of - the facts that describe the hardware rather than measure its state.
 *
 * All of it is free of permissions: describing a sensor is not reading one, and the Wi-Fi capability
 * queries run under `ACCESS_WIFI_STATE`, which the watch manifest has declared for years. Live body
 * readings are deliberately absent - the strategic Non-goals rule them out on the S2013 ground that
 * they need a Play review against the store description (S2165 §2).
 *
 * Null everywhere means the watch would not answer, and an empty list means it answered that it has
 * none - the report says those two differently.
 */
interface WearHardwareDataSource {

    val sensors: List<WearSensorDescriptor>?

    val supportedAbis: List<String>?

    val socManufacturer: String?

    val socModel: String?

    val cpuCoreCount: Int?

    val lowRamDevice: Boolean?

    val supportedWifiBands: List<String>?

    val supportedWifiStandards: List<String>?
}
