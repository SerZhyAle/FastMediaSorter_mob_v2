package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.StatisticsRepository
import com.sza.fastmediasorter.domain.stats.StatsCategory
import com.sza.fastmediasorter.domain.stats.StatsCategoryAvailability
import com.sza.fastmediasorter.domain.stats.StatsSnapshot
import javax.inject.Inject

/**
 * Reads the all-time statistics snapshot for the dashboard (S0473 Phase 04).
 *
 * Thin orchestration only: fetches the snapshot from the repository and exposes the
 * flavor-available category set. No formatting and no Android dependencies - presentation
 * mapping lives in the ViewModel and [com.sza.fastmediasorter.ui.statistics.StatisticsRowFormatter].
 */
class GetStatisticsUseCase @Inject constructor(
    private val repository: StatisticsRepository,
    private val availability: StatsCategoryAvailability,
) {

    /** All-time snapshot: always-on baseline plus detailed opt-in counters. */
    suspend operator fun invoke(): StatsSnapshot = repository.getSnapshot()

    /** Categories that make sense for the current flavor (unavailable ones are hidden, not zeroed). */
    fun availableCategories(): Set<StatsCategory> = availability.availableCategories()
}
