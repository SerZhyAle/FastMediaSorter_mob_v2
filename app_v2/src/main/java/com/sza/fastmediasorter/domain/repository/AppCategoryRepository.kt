package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import kotlinx.coroutines.flow.Flow

/** S4530: the persisted category catalog of the all-apps screen. */
interface AppCategoryRepository {

    fun observeCatalog(): Flow<AppCategoryCatalog>

    /**
     * Applies [transform] atomically against concurrent writers. A failure is returned rather than
     * swallowed, so a caller never presents a change that did not persist as done.
     */
    suspend fun update(transform: (AppCategoryCatalog) -> AppCategoryCatalog): Result<AppCategoryCatalog>
}
