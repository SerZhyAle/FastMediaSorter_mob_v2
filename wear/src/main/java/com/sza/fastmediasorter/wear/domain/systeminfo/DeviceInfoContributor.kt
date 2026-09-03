package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.repository.WearHardwareDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import javax.inject.Inject

/**
 * What this watch is: the name it answers to, the system it runs, and the silicon underneath.
 *
 * Model, OS version and API level were here before and are the part a settings screen also shows; the
 * chipset, instruction sets, core count and low-memory flag are not shown anywhere on the watch and
 * qualify the three above rather than deserving a section of their own (S2165 §6 item 1).
 */
class DeviceInfoContributor @Inject constructor(
    private val dataSource: WearSystemInfoDataSource,
    private val hardware: WearHardwareDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.DEVICE

    override suspend fun sections(): List<WearSystemInfoSection> = listOf(
        section(
            titleRes = R.string.system_info_section_device,
            fields = listOfNotNull(
                text(R.string.system_info_model, deviceName()),
                text(R.string.system_info_os_version, dataSource.osVersion),
                text(R.string.system_info_api_level, dataSource.apiLevel?.toString()),
                text(R.string.system_info_chipset, chipset()),
                text(R.string.system_info_abi, hardware.supportedAbis?.joinToString(", ")),
                text(R.string.system_info_cores, hardware.cpuCoreCount?.toString()),
                lowRam()
            ),
            emptyReasonRes = R.string.system_info_empty_unreadable
        )
    )

    /** Only shown when true - "this watch is not a low-memory device" is not news to anyone. */
    private fun lowRam() = hardware.lowRamDevice
        ?.takeIf { low -> low }
        ?.let { label(R.string.system_info_low_ram, R.string.system_info_yes) }

    private fun chipset(): String? {
        val vendor = hardware.socManufacturer?.trim().orEmpty()
        val part = hardware.socModel?.trim().orEmpty()
        // Same rule the model line uses: a vendor already spelled inside the part number is not
        // printed twice.
        val partCarriesVendor = vendor.isNotEmpty() && part.startsWith(vendor, ignoreCase = true)
        val name = when {
            part.isEmpty() -> vendor
            partCarriesVendor -> part
            else -> listOf(vendor, part).filter { piece -> piece.isNotEmpty() }.joinToString(" ")
        }
        return name.ifEmpty { null }
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
}
