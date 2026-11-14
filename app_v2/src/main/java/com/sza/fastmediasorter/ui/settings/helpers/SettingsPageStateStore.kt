package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import androidx.core.content.edit
import com.sza.fastmediasorter.core.debug.StrictModeHelper

/**
 * S4621: remembers the last settings page and each page's scroll offset by stable destination id
 * (SETTINGS-NAVIGATION 2.1 section 4 rule 3).
 *
 * The pager position is never stored: a flavor page inserted before About would make a stored
 * position name another page on the next open.
 */
class SettingsPageStateStore(context: Context) {

    private val prefs = StrictModeHelper.allowDiskReads {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Last page id, or null before the first visit. [idAtLegacyPosition] maps a position saved by the
     * tab strip of earlier versions onto its id, once; the position key is then dropped.
     */
    fun lastDestinationId(idAtLegacyPosition: (Int) -> String?): String? {
        val stored = StrictModeHelper.allowDiskReads { prefs.getString(KEY_LAST_DESTINATION_ID, null) }
        if (stored != null) return stored
        val legacy = StrictModeHelper.allowDiskReads { prefs.getInt(KEY_LEGACY_TAB_POSITION, -1) }
        val migrated = idAtLegacyPosition(legacy).takeIf { legacy >= 0 }
        StrictModeHelper.allowDiskWrites {
            prefs.edit {
                remove(KEY_LEGACY_TAB_POSITION)
                if (migrated != null) putString(KEY_LAST_DESTINATION_ID, migrated)
            }
        }
        return migrated
    }

    fun saveLastDestinationId(id: String) {
        StrictModeHelper.allowDiskWrites { prefs.edit { putString(KEY_LAST_DESTINATION_ID, id) } }
    }

    fun scrollOffset(id: String): Int =
        StrictModeHelper.allowDiskReads { prefs.getInt(scrollKey(id), 0) }

    fun saveScrollOffset(id: String, offset: Int) {
        StrictModeHelper.allowDiskWrites { prefs.edit { putInt(scrollKey(id), offset) } }
    }

    private fun scrollKey(id: String) = "$KEY_SCROLL_PREFIX$id"

    private companion object {
        const val PREFS_NAME = "settings_state"
        const val KEY_LAST_DESTINATION_ID = "last_destination_id"
        const val KEY_LEGACY_TAB_POSITION = "last_tab_position"
        const val KEY_SCROLL_PREFIX = "scroll_"
    }
}
