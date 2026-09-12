package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.WearHardwareDataSource
import javax.inject.Inject

/**
 * What this watch's Wi-Fi radio is capable of - not what it is connected to.
 *
 * The distinction is the boundary with the network monitor, which the strategic Non-goals draw
 * explicitly: channel, network name, signal level and carrier belong there and are deliberately not
 * repeated here. Capability is a property of the hardware and belongs with the rest of what the watch
 * is made of.
 */
class RadioCapabilityContributor @Inject constructor(
    private val dataSource: WearHardwareDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.RADIO

    override suspend fun sections(): List<WearSystemInfoSection> = listOf(
        section(
            titleRes = R.string.system_info_section_radio,
            fields = listOfNotNull(
                enumerated(R.string.system_info_radio_bands, dataSource.supportedWifiBands),
                enumerated(R.string.system_info_radio_standards, dataSource.supportedWifiStandards)
            ),
            emptyReasonRes = R.string.system_info_empty_unreadable
        )
    )

    private fun enumerated(labelRes: Int, entries: List<String>?): WearSystemInfoField? = entries
        ?.takeIf { names -> names.isNotEmpty() }
        ?.let { names -> WearSystemInfoField(labelRes, WearSystemInfoValue.Enumerated(names)) }
}
