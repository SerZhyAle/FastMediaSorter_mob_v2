package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which registered resources the owner marked for transfer to the watch.
 *
 * An absent key means "nothing selected", never "everything": an update that read a missing set as
 * "select all" would silently push every registered resource to the watch on the first transfer.
 * Ids are stored as strings because SharedPreferences has no long-set type.
 */
@Singleton
class WearResourceSelectionRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        StrictModeHelper.allowDiskReads {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

    fun hasSavedSelection(): Boolean = StrictModeHelper.allowDiskReads {
        prefs.contains(KEY_SELECTED_IDS)
    }

    fun getSelectedIds(): Set<Long> = StrictModeHelper.allowDiskReads {
        prefs.getStringSet(KEY_SELECTED_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    fun setSelectedIds(ids: Set<Long>) {
        StrictModeHelper.allowDiskWrites {
            prefs.edit()
                .putStringSet(KEY_SELECTED_IDS, ids.map { it.toString() }.toSet())
                .apply()
        }
    }

    fun selectAll(allIds: Set<Long>) {
        setSelectedIds(allIds)
    }

    private companion object {
        const val PREFS_NAME = "wear_resource_selection"
        const val KEY_SELECTED_IDS = "selected_ids"
    }
}
