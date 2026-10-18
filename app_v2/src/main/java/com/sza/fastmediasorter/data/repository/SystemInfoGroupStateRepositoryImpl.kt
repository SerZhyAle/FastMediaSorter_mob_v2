package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.data.local.preferences.CollapsibleSectionStore
import com.sza.fastmediasorter.data.local.preferences.SharedPreferencesCollapsibleSectionStore
import com.sza.fastmediasorter.domain.repository.SystemInfoGroupStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SystemInfoGroupStateRepository] backed by the consolidated [CollapsibleSectionStore] namespace,
 * under keys `system_info__<state key>`.
 */
@Singleton
class SystemInfoGroupStateRepositoryImpl(
    private val store: CollapsibleSectionStore,
) : SystemInfoGroupStateRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(SharedPreferencesCollapsibleSectionStore(context))

    override fun isExpanded(stateKey: String): Boolean = store.isExpanded(storeKey(stateKey), DEFAULT_EXPANDED)

    override fun setExpanded(stateKey: String, expanded: Boolean) {
        store.setExpanded(storeKey(stateKey), expanded)
    }

    private fun storeKey(stateKey: String): String = "$KEY_PREFIX$stateKey"

    private companion object {
        const val DEFAULT_EXPANDED = false
        const val KEY_PREFIX = "system_info__"
    }
}
