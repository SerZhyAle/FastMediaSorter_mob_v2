package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.data.launcher.AppShortcutDataSource
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S0427: lists an installed app's published quick actions. Empty when this app is not the active
 * launcher, when the target publishes none, or when the platform refuses the query.
 */
class QueryAppShortcutsUseCase @Inject constructor(
    private val dataSource: AppShortcutDataSource,
) {

    suspend operator fun invoke(packageName: String): List<AppShortcut> = withContext(Dispatchers.IO) {
        dataSource.query(packageName).filter { it.label.isNotBlank() }
    }
}
