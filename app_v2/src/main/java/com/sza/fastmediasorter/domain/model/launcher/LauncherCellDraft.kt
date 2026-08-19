package com.sza.fastmediasorter.domain.model.launcher

/**
 * S1772: what a caller knows about a cell it wants placed, before the desktop supplies the rest.
 *
 * Orientation and the timestamp are deliberately absent - they belong to the desktop that seats the
 * cell, not to the screen asking for it. Grouped because the add entry point had grown to eight
 * arguments, four of which always travel together.
 */
data class LauncherCellDraft(
    val kind: LauncherCellKind,
    val target: String,
    val spanW: Int,
    val spanH: Int,
    /** Resource whose file list should be remembered once the cell exists, when the cell has one. */
    val rememberFileListResourceId: Long? = null,
    /**
     * S1742: the caption the cell carries from birth, for a cell whose name is not derivable from its
     * target - a user-created section has no preset label to fall back on.
     */
    val labelOverride: String? = null,
)
