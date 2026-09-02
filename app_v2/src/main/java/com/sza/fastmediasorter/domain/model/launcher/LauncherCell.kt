package com.sza.fastmediasorter.domain.model.launcher

/** Which of the two independent desktop layouts a cell belongs to (strategic §3.3). */
enum class LauncherOrientation {
    PORTRAIT,
    LANDSCAPE,
}

enum class LauncherCellKind {
    SHORTCUT,
    GADGET,

    /**
     * S1428: a full-width titled header. Its own kind rather than a [SHORTCUT] command prefix because
     * the shortcut binding path attaches the long-press listener and the "show actions" accessibility
     * action before the command is parsed, and strategic §6.8 rules the header is not long-pressable.
     */
    SECTION,
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
    val screenIndex: Int = 0,
)
