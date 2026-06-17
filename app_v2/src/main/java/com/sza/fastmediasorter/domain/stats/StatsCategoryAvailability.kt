package com.sza.fastmediasorter.domain.stats

import com.sza.fastmediasorter.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for category-level visibility in the statistics dashboard (S0473).
 *
 * Decides which metric groups make sense for the current build from the existing `SUPPORT_*`
 * capability flags (not `IS_*` flavor guards). A category unavailable in the flavor is hidden
 * entirely rather than shown with zeros (strategic §3.2). Per-row zero-hiding within a visible
 * category is the dashboard's responsibility.
 */
@Singleton
class StatsCategoryAvailability @Inject constructor() {

    fun isCategoryAvailable(category: StatsCategory): Boolean = when (category) {
        // Network/cloud sources only exist where the build can reach them.
        StatsCategory.SOURCES -> BuildConfig.SUPPORT_CLOUD || BuildConfig.SUPPORT_LOCAL_NETWORK
        // Operations, capture, viewing, editing and usage exist in every flavor (images are universal).
        else -> true
    }

    fun availableCategories(): Set<StatsCategory> =
        StatsCategory.entries.filter { isCategoryAvailable(it) }.toSet()
}
