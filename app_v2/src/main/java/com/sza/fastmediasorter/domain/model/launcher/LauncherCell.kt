package com.sza.fastmediasorter.domain.model.launcher

/** Which of the two independent desktop layouts a cell belongs to (strategic §3.3). */
enum class LauncherOrientation {
    PORTRAIT,
    LANDSCAPE,
}

enum class LauncherCellKind {
    SHORTCUT,
    GADGET,
}

/**
 * S0404: one item the user placed on the launcher desktop. A shortcut occupies a single grid cell;
 * a gadget spans [spanW] x [spanH] cells. Portrait and landscape hold separate rows, so arranging
 * one orientation never disturbs the other.
 */
data class LauncherCell(
    val id: Long,
    val orientation: LauncherOrientation,
    val rowIndex: Int,
    val colIndex: Int,
    val spanW: Int,
    val spanH: Int,
    val kind: LauncherCellKind,
    /** Encoded [LauncherCellCommand] for SHORTCUT; the gadget registry key for GADGET. */
    val target: String,
    val labelOverride: String?,
    val addedAt: Long,
)
