package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.repository.AppCategoryRepository
import java.util.UUID
import javax.inject.Inject

/**
 * S4530: creates a named category and returns its id.
 *
 * A name the user already has, in any letter case, returns that category instead of a twin. The new
 * category stays in the catalog while empty, so it can be chosen for an app later (strategic §3.1).
 */
class CreateAppCategoryUseCase @Inject constructor(
    private val repository: AppCategoryRepository,
) {

    suspend operator fun invoke(name: String): Result<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("A category name is blank"))
        var id = ""
        return repository.update { catalog ->
            val existing = catalog.categories.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            if (existing != null) {
                id = existing.id
                catalog
            } else {
                val created = AppCategory(
                    id = AppCategoryCatalog.USER_CATEGORY_ID_PREFIX + UUID.randomUUID(),
                    name = trimmed,
                    systemCategory = null,
                )
                id = created.id
                catalog.copy(
                    categories = catalog.categories + created,
                    order = if (catalog.order.isEmpty()) catalog.order else catalog.order + created.id,
                )
            }
        }.map { id }
    }
}
