package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import javax.inject.Inject

/** How much working memory the whole watch has left, which is what decides whether this app survives. */
class MemoryInfoContributor @Inject constructor(
    private val dataSource: WearSystemInfoDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.MEMORY

    override suspend fun sections(): List<WearSystemInfoSection> = listOf(
        section(
            titleRes = R.string.system_info_section_memory,
            fields = listOfNotNull(
                text(R.string.system_info_free, dataSource.availableMemoryBytes?.let(::formatBytes)),
                text(R.string.system_info_total, dataSource.totalMemoryBytes?.let(::formatBytes))
            ),
            emptyReasonRes = R.string.system_info_empty_unreadable
        )
    )
}
