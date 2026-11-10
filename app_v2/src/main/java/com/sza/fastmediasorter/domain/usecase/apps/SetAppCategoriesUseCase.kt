package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.repository.AppCategoryRepository
import javax.inject.Inject

/**
 * S4530: replaces the categories of one app; an empty set sends it to "Unknown".
 *
 * The package is marked seeded as well, so a later refresh never overrides the user's choice.
 */
class SetAppCategoriesUseCase @Inject constructor(
    private val repository: AppCategoryRepository,
) {

    suspend operator fun invoke(packageName: String, categoryIds: Set<String>): Result<AppCategoryCatalog> =
        repository.update { catalog ->
            val known = catalog.categories.mapTo(HashSet()) { it.id }
            val kept = categoryIds.filterTo(LinkedHashSet()) { it in known }
            val assignments = if (kept.isEmpty()) {
                catalog.assignments - packageName
            } else {
                catalog.assignments + (packageName to kept)
            }
            catalog.copy(assignments = assignments, seededPackages = catalog.seededPackages + packageName)
        }
}
