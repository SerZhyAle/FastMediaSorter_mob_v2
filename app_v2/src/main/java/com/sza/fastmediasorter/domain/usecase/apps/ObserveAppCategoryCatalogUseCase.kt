package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.repository.AppCategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** S4530: the category catalog stream the all-apps screen groups by. */
class ObserveAppCategoryCatalogUseCase @Inject constructor(
    private val repository: AppCategoryRepository,
) {
    operator fun invoke(): Flow<AppCategoryCatalog> = repository.observeCatalog()
}
