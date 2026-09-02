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

    override fun isCollapsed(orientation: LauncherOrientation, screenIndex: Int, sectionTarget: String): Boolean {
        return !store.isExpanded(keyFor(orientation, screenIndex, sectionTarget), EXPANDED_BY_DEFAULT)
    }

    override fun reveal(orientation: LauncherOrientation, screenIndex: Int, sectionTarget: String) {
        setExpanded(orientation, screenIndex, sectionTarget, true)
    }

    override fun setExpanded(
        orientation: LauncherOrientation,
        screenIndex: Int,
        sectionTarget: String,
        expanded: Boolean,
    ) {
        store.setExpanded(keyFor(orientation, screenIndex, sectionTarget), expanded)
    }

    internal companion object {
        /** `<screen>__<section>` is the store's own key shape; the orientation joins the screen half. */
        const val KEY_SCREEN = "launcher_desktop__"

        /** Strategic §6.8: a section is open until the user folds it, so "visible without a tap" holds. */
        const val EXPANDED_BY_DEFAULT = true

        /** The desktop screen whose keys predate S2317 and therefore carry no screen segment. */
        const val FIRST_SCREEN = 0

        /** Distinguishes the screen segment from a target, which no section key may start with. */
        const val SCREEN_SEGMENT_PREFIX = "s"

        /**
         * Portrait and landscape are two independent layouts (strategic §6.3), so the orientation is part
         * of the key: a section folded in one says nothing about the same section in the other.
         *
         * S2317 adds the desktop screen for the same reason, because the starter set seeds one
         * `SECTION_WIDGETS` header on screen 0 and another on screen 1 - one key for both folded them
         * together. The first screen deliberately keeps the pre-S2317 spelling rather than gaining an
         * `s0__` segment: every fold already stored was written under that exact string, and a screen
         * that never had a state of its own starts at [EXPANDED_BY_DEFAULT] anyway, so shaping the key
         * this way needs no migration and loses nothing.
         */
        fun keyFor(orientation: LauncherOrientation, screenIndex: Int, target: String): String {
            val prefix = "$KEY_SCREEN${orientation.name}__"
            return if (screenIndex <= FIRST_SCREEN) {
                "$prefix$target"
            } else {
                "$prefix$SCREEN_SEGMENT_PREFIX${screenIndex}__$target"
            }
        }
    }
}
