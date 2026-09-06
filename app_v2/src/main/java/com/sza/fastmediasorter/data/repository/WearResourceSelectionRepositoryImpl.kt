package com.sza.fastmediasorter.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which registered resources the owner marked for transfer to the watch.
 *
 * An absent key means "nothing selected", never "everything": an update that read a missing set as
 * "select all" would silently push every registered resource to the watch on the first transfer.
 * Ids are stored as strings because SharedPreferences has no long-set type.
 *
 * S2515: every member suspends and moves itself to IO. This class previously wrapped each access in
 * `StrictModeHelper.allowDiskReads`/`allowDiskWrites`, which stopped the warning being printed
 * without stopping the disk access it warned about - so the send path stayed on the main thread while
 * looking clean. The suppressions are gone along with the reason they existed.
 */
@Singleton
class WearResourceSelectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Resolved per access rather than in a field initialiser: a @Singleton field would parse the XML
    // at first injection, on whatever thread Hilt happened to construct it, which no suspend member
    // could move off. Android caches the instance after the first load, so this costs a map lookup.
    private val prefs
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun hasSavedSelection(): Boolean = withContext(Dispatchers.IO) {
        prefs.contains(KEY_SELECTED_IDS)
    }

    suspend fun getSelectedIds(): Set<Long> = withContext(Dispatchers.IO) {
        prefs.getStringSet(KEY_SELECTED_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    suspend fun setSelectedIds(ids: Set<Long>) {
        withContext(Dispatchers.IO) {
            Timber.d("S2515: selection write of ${ids.size} id(s) on ${Thread.currentThread().name}")
            prefs.edit()
                .putStringSet(KEY_SELECTED_IDS, ids.map { it.toString() }.toSet())
                .apply()
        }
    }

    suspend fun selectAll(allIds: Set<Long>) {
        setSelectedIds(allIds)
    }

    private companion object {
        const val PREFS_NAME = "wear_resource_selection"
        const val KEY_SELECTED_IDS = "selected_ids"
    }
}
