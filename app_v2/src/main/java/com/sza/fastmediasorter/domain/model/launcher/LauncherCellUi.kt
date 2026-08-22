package com.sza.fastmediasorter.domain.model.launcher

import com.sza.fastmediasorter.domain.usecase.launcher.LauncherCommandVisual

/**
 * S0404: one desktop cell ready to render.
 *
 * [visual] is null for a GADGET cell (the gadget view draws itself) and for a SHORTCUT whose target
 * this build cannot resolve - [LauncherCell.kind] tells the two apart. [modeBadge] is set only for a
 * resource shortcut, where the same resource can be pinned in several view modes.
 */
data class LauncherCellUi(
    val cell: LauncherCell,
    val visual: LauncherCommandVisual?,
    val modeBadge: LauncherResourceMode?,
    val contactAction: LauncherContactAction? = null,
    val messengerPackage: String? = null,
)
