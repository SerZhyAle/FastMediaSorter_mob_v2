package com.sza.fastmediasorter.wear.domain.usecase

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

private const val BYTES_IN_KIB = 1024.0

/** Below one whole gibibyte the report reads in megabytes, where the number still says something. */
private const val GIB_THRESHOLD = 1.0

/**
 * Assembles what the watch can say about itself into sections of name-value pairs.
 *
 * Runs off the main thread because the watch is stricter than the phone about battery and about a frame
 * it cannot draw, and touches no network: the phone question is answered from the local Data Layer.
 */
class GatherWearSystemInfoUseCase @Inject constructor(
    private val dataSource: WearSystemInfoDataSource
) {

    suspend operator fun invoke(): List<WearSystemInfoSection> = withContext(Dispatchers.IO) {
        Timber.d("S1733: wear system info gathered")
        listOfNotNull(
            section(R.string.system_info_section_device, deviceFields()),
            section(R.string.system_info_section_app, appFields()),
            section(R.string.system_info_section_memory, memoryFields()),
            section(R.string.system_info_section_storage, storageFields()),
            section(R.string.system_info_section_phone, phoneFields())
        )
    }

    private fun deviceFields(): List<WearSystemInfoField> = listOfNotNull(
        text(R.string.system_info_model, deviceName()),
        text(R.string.system_info_os_version, dataSource.osVersion),
        text(R.string.system_info_api_level, dataSource.apiLevel?.toString())
    )

    private fun appFields(): List<WearSystemInfoField> = listOfNotNull(
        text(R.string.system_info_app_version, dataSource.appVersion),
        text(R.string.system_info_build_number, dataSource.buildNumber)
    )

    private fun memoryFields(): List<WearSystemInfoField> = listOfNotNull(
        text(R.string.system_info_free, dataSource.availableMemoryBytes?.let(::formatBytes)),
        text(R.string.system_info_total, dataSource.totalMemoryBytes?.let(::formatBytes))
    )

    private fun storageFields(): List<WearSystemInfoField> = listOfNotNull(
        text(R.string.system_info_free, dataSource.availableStorageBytes?.let(::formatBytes)),
        text(R.string.system_info_total, dataSource.totalStorageBytes?.let(::formatBytes))
    )

    private suspend fun phoneFields(): List<WearSystemInfoField> {
        val answer = if (dataSource.isPhoneConnected()) {
            R.string.system_info_phone_connected
        } else {
            R.string.system_info_phone_not_connected
        }
        return listOf(WearSystemInfoField(R.string.system_info_phone_link, WearSystemInfoValue.Label(answer)))
    }

    private fun deviceName(): String? {
        val manufacturer = dataSource.manufacturer?.trim().orEmpty()
        val model = dataSource.model?.trim().orEmpty()
        // Several vendors already repeat their own name inside the model string, and printing it twice
        // reads as a defect rather than as detail.
        val modelCarriesVendor = manufacturer.isNotEmpty() && model.startsWith(manufacturer, ignoreCase = true)
        val name = when {
            model.isEmpty() -> manufacturer
            modelCarriesVendor -> model
            else -> listOf(manufacturer, model).filter { part -> part.isNotEmpty() }.joinToString(" ")
        }
        return name.ifEmpty { null }
    }

    /**
     * Formatted with [Locale.US] on purpose: this is a measurement, not prose, and letting the decimal
     * separator follow the watch's locale would make the same reading print differently on two watches.
     */
    private fun formatBytes(bytes: Long): String {
        val mebibytes = bytes.toDouble() / BYTES_IN_KIB / BYTES_IN_KIB
        val gibibytes = mebibytes / BYTES_IN_KIB
        return if (gibibytes >= GIB_THRESHOLD) {
            String.format(Locale.US, "%.1f GB", gibibytes)
        } else {
            String.format(Locale.US, "%.0f MB", mebibytes)
        }
    }

    /** A section with nothing readable in it is dropped rather than shown as an empty heading. */
    private fun section(
        @StringRes titleRes: Int,
        fields: List<WearSystemInfoField>
    ): WearSystemInfoSection? = if (fields.isEmpty()) null else WearSystemInfoSection(titleRes, fields)

    private fun text(@StringRes labelRes: Int, value: String?): WearSystemInfoField? =
        value?.let { resolved -> WearSystemInfoField(labelRes, WearSystemInfoValue.Text(resolved)) }
}
