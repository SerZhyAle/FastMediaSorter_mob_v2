package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.data.local.preferences.CollapsibleSectionStore
import com.sza.fastmediasorter.data.local.preferences.SharedPreferencesCollapsibleSectionStore
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherSectionVisibilityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LauncherSectionVisibilityRepository] backed by [CollapsibleSectionStore].
 *
 * Single source of truth for the launcher desktop section collapse key formula and defaults.
 */
@Singleton
class LauncherSectionVisibilityRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : LauncherSectionVisibilityRepository {

    // Consolidated SharedPreferences storage for collapsible sections
    private val store: CollapsibleSectionStore = SharedPreferencesCollapsibleSectionStore(context)

    override fun isCollapsed(orientation: LauncherOrientation, sectionTarget: String): Boolean {
        return !store.isExpanded(keyFor(orientation, sectionTarget), EXPANDED_BY_DEFAULT)
    }

    override fun reveal(orientation: LauncherOrientation, sectionTarget: String) {
        setExpanded(orientation, sectionTarget, true)
    }

    override fun setExpanded(orientation: LauncherOrientation, sectionTarget: String, expanded: Boolean) {
        store.setExpanded(keyFor(orientation, sectionTarget), expanded)
    }

    /**
     * Portrait and landscape are two independent layouts (strategic §6.3), so the orientation is part of
     * the key: a section folded in one says nothing about the same section in the other.
     */
    private fun keyFor(orientation: LauncherOrientation, target: String): String =
        "$KEY_SCREEN${orientation.name}__$target"

    private companion object {
        /** `<screen>__<section>` is the store's own key shape; the orientation joins the screen half. */
        const val KEY_SCREEN = "launcher_desktop__"

        /** Strategic §6.8: a section is open until the user folds it, so "visible without a tap" holds. */
        const val EXPANDED_BY_DEFAULT = true
    }
}
