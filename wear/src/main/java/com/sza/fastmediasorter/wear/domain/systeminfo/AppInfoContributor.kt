package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import javax.inject.Inject

/** Which build of this app the watch is running - the one fact a bug report is useless without. */
class AppInfoContributor @Inject constructor(
    private val dataSource: WearSystemInfoDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.APP

    override suspend fun sections(): List<WearSystemInfoSection> = listOf(
        section(
            titleRes = R.string.system_info_section_app,
            fields = listOfNotNull(
                text(R.string.system_info_app_version, dataSource.appVersion),
                text(R.string.system_info_build_number, dataSource.buildNumber)
            ),
            emptyReasonRes = R.string.system_info_empty_unreadable
        )
    )
}
