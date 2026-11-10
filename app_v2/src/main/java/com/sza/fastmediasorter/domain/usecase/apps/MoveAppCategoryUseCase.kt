package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.repository.AppCategoryRepository
import javax.inject.Inject

/**
 * S4530: moves one category a place up or down among the groups as drawn.
 *
 * The first move freezes the drawn order into the catalog, which is what ends the alphabetical default.
 * Hidden empty categories keep their relative order after the drawn ones; "Unknown" is never part of it.
 */
class MoveAppCategoryUseCase @Inject constructor(
    private val repository: AppCategoryRepository,
) {

    suspend operator fun invoke(
        categoryId: String,
        displayedOrder: List<String>,
        offset: Int,
    ): Result<AppCategoryCatalog> = repository.update { catalog ->
        val known = catalog.categories.mapTo(HashSet()) { it.id }
        val drawn = displayedOrder.filter { it in known }.distinct()
        val rest = catalog.orderedCategories { it.name.orEmpty() }.map { it.id }.filter { it !in drawn }
        val order = (drawn + rest).toMutableList()
        val from = order.indexOf(categoryId)
        val to = from + offset
        if (from < 0 || to !in order.indices) {
            catalog
        } else {
            order[from] = order[to]
            order[to] = categoryId
            catalog.copy(order = order)
        }
    }
}
