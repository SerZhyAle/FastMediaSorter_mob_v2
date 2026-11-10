package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.repository.AppCategoryRepository
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import javax.inject.Inject

/**
 * S4530: gives every app seen for the first time the category its publisher declared.
 *
 * Each package is decided once and remembered in [AppCategoryCatalog.seededPackages]: an app the user
 * moved or cleared is never handed its system category again, and an undefined one stays in "Unknown"
 * (strategic ADR-1, §11 criterion 4).
 */
class SeedAppCategoriesUseCase @Inject constructor(
    private val appCategories: AppCategoryRepository,
    private val installedApps: InstalledAppsRepository,
) {

    /** Follows the installed-app cache for as long as the caller's scope lives. */
    suspend fun run() {
        installedApps.observeApps().distinctUntilChanged().collect { seed(it) }
    }

    suspend fun seed(apps: List<InstalledApp>): Result<AppCategoryCatalog> =
        appCategories.update { catalog ->
            val fresh = apps.filter { it.packageName !in catalog.seededPackages }
            if (fresh.isEmpty()) catalog else seedInto(catalog, fresh)
        }

    private fun seedInto(catalog: AppCategoryCatalog, fresh: List<InstalledApp>): AppCategoryCatalog {
        Timber.d("S4530: seed new packages")
        val categories = catalog.categories.toMutableList()
        val order = catalog.order.toMutableList()
        val assignments = catalog.assignments.toMutableMap()
        fresh.filter { it.category != CATEGORY_UNDEFINED }.forEach { app ->
            val existing = categories.firstOrNull { it.systemCategory == app.category }
            val category = existing ?: AppCategory(
                id = AppCategoryCatalog.SYSTEM_CATEGORY_ID_PREFIX + app.category,
                name = null,
                systemCategory = app.category,
            ).also { created ->
                categories += created
                if (order.isNotEmpty()) order += created.id
            }
            assignments[app.packageName] = setOf(category.id)
        }
        return catalog.copy(
            categories = categories,
            order = order,
            assignments = assignments,
            seededPackages = catalog.seededPackages + fresh.map { it.packageName },
        )
    }

    private companion object {
        /** `ApplicationInfo.CATEGORY_UNDEFINED`, spelled out so the domain layer stays Android-free. */
        const val CATEGORY_UNDEFINED = -1
    }
}
