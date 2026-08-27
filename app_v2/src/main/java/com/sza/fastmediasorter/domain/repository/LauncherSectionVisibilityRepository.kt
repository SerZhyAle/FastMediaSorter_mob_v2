package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation

/**
 * Domain port for section collapse/expansion state on the launcher desktop.
 *
 * Address a section by its header cell's encoded [sectionTarget], never by its row.
 * This port is read only after a cell has been written and never while an anchor is being chosen.
 */
interface LauncherSectionVisibilityRepository {

    fun isCollapsed(orientation: LauncherOrientation, sectionTarget: String): Boolean

    fun reveal(orientation: LauncherOrientation, sectionTarget: String)

    fun setExpanded(orientation: LauncherOrientation, sectionTarget: String, expanded: Boolean)
}
