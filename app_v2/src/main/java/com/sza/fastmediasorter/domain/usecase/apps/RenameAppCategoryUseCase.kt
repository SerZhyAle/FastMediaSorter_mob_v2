package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.repository.AppCategoryRepository
import javax.inject.Inject

/**
 * S4563: gives a category a name of the user's own.
 *
 * A seeded system category takes the name too and keeps its id and system category, so a later seeding pass
 * still finds it and adds no twin; from then on it no longer follows a language change. The name is
 * compared with the labels the screen draws, not with the stored names, because a seeded category stores
 * none and two groups must never look identical.
 */
class RenameAppCategoryUseCase @Inject constructor(
    private val repository: AppCategoryRepository,
) {

    suspend operator fun invoke(
        categoryId: String,
        name: String,
        labelOf: (AppCategory) -> String,
    ): Result<AppCategoryCatalog> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("A category name is blank"))
        var taken = false
        val result = repository.update { catalog ->
            taken = catalog.categories.any { other ->
                other.id != categoryId && labelOf(other).equals(trimmed, ignoreCase = true)
            }
            if (taken) catalog else catalog.withName(categoryId, trimmed)
        }
        return if (taken) Result.failure(AppCategoryNameTakenException(trimmed)) else result
    }

    private fun AppCategoryCatalog.withName(categoryId: String, name: String): AppCategoryCatalog =
        copy(categories = categories.map { if (it.id == categoryId) it.copy(name = name) else it })
}

/** S4563: another category already shows this name; the catalog was left as it was. */
class AppCategoryNameTakenException(name: String) :
    IllegalArgumentException("A category named \"$name\" already exists")
