package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.WearHardwareDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearSensorDescriptor
import java.util.Locale
import javax.inject.Inject

/**
 * Which sensors this watch physically carries, with what each one costs to run.
 *
 * This is the sharpest example of the criterion the whole report is selected by: the watch's own
 * settings show none of it, and it is the difference between "my watch does not track that" and "the
 * app is not using it" (S2165 §6 item 1).
 *
 * Collapsed to a count, because a modern watch answers several dozen and every entry is far longer
 * than the fourteen characters that still fit half a row (§5.1 pillar D).
 */
class SensorsInfoContributor @Inject constructor(
    private val dataSource: WearHardwareDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.SENSORS

    override suspend fun sections(): List<WearSystemInfoSection> {
        val sensors = dataSource.sensors
        return listOf(
            WearSystemInfoSection(
                titleRes = R.string.system_info_section_sensors,
                fields = if (sensors.isNullOrEmpty()) emptyList() else listOf(entry(sensors)),
                emptyReasonRes = emptinessReason(sensors)
            )
        )
    }

    /** "The watch would not answer" and "this watch has none" are different facts, and it says which. */
    private fun emptinessReason(sensors: List<WearSensorDescriptor>?): Int? = when {
        sensors == null -> R.string.system_info_empty_unreadable
        sensors.isEmpty() -> R.string.system_info_empty_unsupported
        else -> null
    }

    private fun entry(sensors: List<WearSensorDescriptor>): WearSystemInfoField = WearSystemInfoField(
        R.string.system_info_sensors_present,
        WearSystemInfoValue.Enumerated(sensors.map(::describe))
    )

    /**
     * Formatted with [Locale.US] on the same reasoning the byte counts use: these are measurements,
     * and a decimal separator that followed the watch's locale would print one reading two ways.
     */
    private fun describe(sensor: WearSensorDescriptor): String = String.format(
        Locale.US,
        "%s - %s - %.2f mA - %.4f",
        sensor.name,
        sensor.vendor,
        sensor.powerMilliAmps,
        sensor.resolution
    )
}
