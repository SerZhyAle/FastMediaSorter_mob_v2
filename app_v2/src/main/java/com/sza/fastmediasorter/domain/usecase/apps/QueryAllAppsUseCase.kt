package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LaunchStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * S1401: the all-apps screen's whole read path - filter by the typed text, then order.
 *
 * The surface receives a finished list and decides nothing about its contents (strategic 5.1).
 */
class QueryAllAppsUseCase @Inject constructor(
    private val repository: InstalledAppsRepository
) {

    operator fun invoke(
        query: String,
        order: InstalledAppSortOrder,
        descending: Boolean
    ): Flow<List<InstalledApp>> =
        combine(repository.observeApps(), repository.observeLaunchStats()) { apps, stats ->
            val ordered = sort(apps.filter { it.matches(query) }, order, stats)
            if (descending) ordered.reversed() else ordered
        }
            // Without this the filter and the sort run wherever the collector lives - the main thread for
            // a UI subscriber - and every recorded launch re-sorts the whole installed-app list on it.
            .flowOn(Dispatchers.Default)

    /**
     * The package name is matched as well as the label, so a user who knows what an app is called
     * internally still finds it when its display name says something else entirely.
     */
    private fun InstalledApp.matches(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        return label.contains(trimmed, ignoreCase = true) ||
            packageName.contains(trimmed, ignoreCase = true)
    }

    private fun sort(
        apps: List<InstalledApp>,
        order: InstalledAppSortOrder,
        stats: Map<String, LaunchStats>
    ): List<InstalledApp> = when (order) {
        InstalledAppSortOrder.LABEL -> apps.sortedWith(byLabel)
        InstalledAppSortOrder.INSTALL_DATE ->
            apps.sortedWith(compareByDescending<InstalledApp> { it.firstInstallTime }.then(byLabel))

        InstalledAppSortOrder.UPDATE_DATE ->
            apps.sortedWith(compareByDescending<InstalledApp> { it.lastUpdateTime }.then(byLabel))

        InstalledAppSortOrder.LAUNCH_FREQUENCY -> sortByFrequency(apps, stats)
        InstalledAppSortOrder.CATEGORY -> sortByCategory(apps)
    }

    /**
     * Falls back to label order while the journal is empty: a brand-new install would otherwise open
     * on an order that looks broken rather than merely unpopulated (strategic 7).
     */
    private fun sortByFrequency(
        apps: List<InstalledApp>,
        stats: Map<String, LaunchStats>
    ): List<InstalledApp> {
        if (stats.isEmpty()) return apps.sortedWith(byLabel)
        return apps.sortedWith(
            compareByDescending<InstalledApp> { statsOf(it, stats)?.launchCount ?: 0 }
                .thenByDescending { statsOf(it, stats)?.lastLaunchedAt ?: 0L }
                .then(byLabel)
        )
    }

    /**
     * Apps the publisher left uncategorised gather in one group at the end instead of scattering
     * through the alphabetical run of a category they do not belong to (strategic 6 item 4).
     */
    private fun sortByCategory(apps: List<InstalledApp>): List<InstalledApp> =
        apps.sortedWith(
            compareBy<InstalledApp> { if (it.category == CATEGORY_UNDEFINED) 1 else 0 }
                .thenBy { if (it.category == CATEGORY_UNDEFINED) 0 else it.category }
                .then(byLabel)
        )

    private fun statsOf(app: InstalledApp, stats: Map<String, LaunchStats>): LaunchStats? =
        stats[LauncherCellCommand.App(app.packageName).encode()]

    private companion object {
        /** `ApplicationInfo.CATEGORY_UNDEFINED`, spelled out so the domain layer stays Android-free. */
        const val CATEGORY_UNDEFINED = -1

        /** Every order ends on this, so identical inputs can never produce two different lists. */
        val byLabel: Comparator<InstalledApp> =
            compareBy(String.CASE_INSENSITIVE_ORDER, InstalledApp::label)
                .thenBy(InstalledApp::packageName)
    }
}
