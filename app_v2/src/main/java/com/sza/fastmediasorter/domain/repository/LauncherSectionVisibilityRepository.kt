package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation

/**
 * Domain port for section collapse/expansion state on the launcher desktop.
 *
 * Address a section by its header cell's [screenIndex] and encoded [sectionTarget] together, never by
 * its row. S2317: the target alone is not an identity - the starter set seeds one `SECTION_WIDGETS`
 * header per screen, so a key without the screen folds both of them with one tap.
 *
 * This port is read only after a cell has been written and never while an anchor is being chosen.
 */
interface LauncherSectionVisibilityRepository {

    fun isCollapsed(orientation: LauncherOrientation, screenIndex: Int, sectionTarget: String): Boolean

    fun reveal(orientation: LauncherOrientation, screenIndex: Int, sectionTarget: String)

    fun setExpanded(orientation: LauncherOrientation, screenIndex: Int, sectionTarget: String, expanded: Boolean)
}
