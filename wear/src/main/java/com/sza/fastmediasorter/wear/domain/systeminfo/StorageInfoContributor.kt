package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import javax.inject.Inject

/**
 * What this app itself occupies on the watch.
 *
 * It used to report free and total space on the volume, which is the figure the watch's own settings
 * screen shows - and its comment claimed it measured the app's directory, which it did not (S2165 §4).
 * The owner settled the replacement on 2026-09-02 (§6 question 8): the app's own footprint and the
 * cache reserve still available to it, both of which the watch answers nowhere else.
 */
class StorageInfoContributor @Inject constructor(
    private val dataSource: WearSystemInfoDataSource
) : WearSystemInfoContributor {

    override val order: Int = WearSystemInfoOrder.STORAGE

    override suspend fun sections(): List<WearSystemInfoSection> = listOf(
        section(
            titleRes = R.string.system_info_section_storage,
            fields = listOfNotNull(
                text(R.string.system_info_storage_app_data, dataSource.appDataBytes?.let(::formatBytes)),
                text(R.string.system_info_storage_app_cache, dataSource.appCacheBytes?.let(::formatBytes)),
                text(
                    R.string.system_info_storage_cache_quota,
                    dataSource.cacheQuotaBytes?.let(::formatBytes)
                )
            ),
            emptyReasonRes = R.string.system_info_empty_unreadable
        )
    )
}
