package com.sza.fastmediasorter.data.local.preferences

import android.content.Context
import com.sza.fastmediasorter.core.debug.StrictModeHelper

/**
 * Single source of truth for collapsible-section expanded state.
 *
 * Keys are caller-supplied `<screen>__<section>` strings
 * (see [com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionsManager]).
 * The abstraction hides the storage medium so it can be swapped later without touching screens.
 */
interface CollapsibleSectionStore {

    fun isExpanded(key: String, default: Boolean): Boolean

    fun setExpanded(key: String, expanded: Boolean)

    companion object {
        /** One consolidated SharedPreferences namespace replacing the legacy per-screen files. */
        const val NAMESPACE = "collapsible_sections_state"
    }
}

/**
 * Default [CollapsibleSectionStore] backed by one consolidated SharedPreferences namespace.
 *
 * Disk access is wrapped in [StrictModeHelper] to mirror the existing settings orchestrators,
 * since expanded state is read synchronously while a screen is being created.
 */
class SharedPreferencesCollapsibleSectionStore(context: Context) : CollapsibleSectionStore {

    private val prefs = StrictModeHelper.allowDiskReads {
        context.applicationContext
            .getSharedPreferences(CollapsibleSectionStore.NAMESPACE, Context.MODE_PRIVATE)
    }

    override fun isExpanded(key: String, default: Boolean): Boolean =
        StrictModeHelper.allowDiskReads { prefs.getBoolean(key, default) }

    override fun setExpanded(key: String, expanded: Boolean) {
        StrictModeHelper.allowDiskWrites { prefs.edit().putBoolean(key, expanded).apply() }
    }
}
