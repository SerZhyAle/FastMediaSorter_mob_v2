package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentKind
import com.sza.fastmediasorter.wear.domain.repository.WearEnvironmentReading
import com.sza.fastmediasorter.wear.domain.repository.WearReadingAccuracy
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * What the watch's environmental sensors are reading at the moment the report is opened.
 *
 * The section next door lists which sensors the watch carries; this one says whether they answer, which
 * is the difference between hardware the specification promises and hardware that works. Every kind
 * keeps its row in all four states rather than disappearing when it has nothing to show, because a watch
 * without a barometer has to be told so (S2459 §11 criterion 3).
 */
class EnvironmentInfoContributor @Inject constructor(
    private val dataSource: WearEnvironmentDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.ENVIRONMENT

    override suspend fun sections(): List<WearSystemInfoSection> {
        val readings = dataSource.sample()
        Timber.d("S2459: environment section built from %s reading(s)", readings?.size)
        return listOf(
            WearSystemInfoSection(
                titleRes = R.string.system_info_section_environment,
                fields = readings.orEmpty().map(::field),
                emptyReasonRes = emptinessReason(readings)
            )
        )
    }

    /** "The watch would not answer" and "this watch has none" are different facts, and it says which. */
    private fun emptinessReason(readings: List<WearEnvironmentReading>?): Int? = when {
        readings == null -> R.string.system_info_empty_unreadable
        readings.isEmpty() -> R.string.system_info_empty_unsupported
        else -> null
    }

    private fun field(reading: WearEnvironmentReading): WearSystemInfoField =
        WearSystemInfoField(labelOf(reading.kind), valueOf(reading))

    private fun labelOf(kind: WearEnvironmentKind): Int = when (kind) {
        WearEnvironmentKind.ILLUMINANCE -> R.string.system_info_environment_light
        WearEnvironmentKind.PRESSURE -> R.string.system_info_environment_pressure
        WearEnvironmentKind.MAGNETIC_FIELD -> R.string.system_info_environment_magnetic
    }

    private fun valueOf(reading: WearEnvironmentReading): WearSystemInfoValue = when (reading) {
        is WearEnvironmentReading.Measured -> measured(reading)
        is WearEnvironmentReading.Silent ->
            WearSystemInfoValue.Label(R.string.system_info_environment_silent)
        is WearEnvironmentReading.Unsupported ->
            WearSystemInfoValue.Label(R.string.system_info_empty_unsupported)
    }

    /**
     * An unreliable sample is reported as a word, never as the number it came with: the platform is
     * saying that number means nothing, and printing it anyway produces exactly the confidently wrong
     * diagnostics S2459 §7 lists as the main risk.
     */
    private fun measured(reading: WearEnvironmentReading.Measured): WearSystemInfoValue =
        if (reading.accuracy == WearReadingAccuracy.UNRELIABLE) {
            WearSystemInfoValue.Label(R.string.system_info_environment_unreliable)
        } else {
            WearSystemInfoValue.Text(format(reading.kind, reading.value))
        }

    /**
     * Formatted with [Locale.US] on the reasoning the rest of the report uses: these are measurements,
     * and a decimal separator following the watch's locale would print one reading two ways. Lux carries
     * no decimals - daylight runs to five figures, where a tenth of one says nothing.
     */
    private fun format(kind: WearEnvironmentKind, value: Float): String = when (kind) {
        WearEnvironmentKind.ILLUMINANCE -> String.format(Locale.US, "%.0f lx", value)
        WearEnvironmentKind.PRESSURE -> String.format(Locale.US, "%.1f hPa", value)
        WearEnvironmentKind.MAGNETIC_FIELD -> String.format(Locale.US, "%.1f uT", value)
    }
}
