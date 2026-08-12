package com.sza.fastmediasorter.ui.common.backgroundop

/**
 * What the hairline background-operation bar renders right now.
 *
 * Three states rather than a nullable percent: `null` would have to mean both "no operation" and
 * "operation running, total not known yet", and those two draw differently.
 */
sealed interface BackgroundOperationBarState {

    /** No background operation is running - the bar is not drawn. */
    data object Hidden : BackgroundOperationBarState

    /** An operation is running while its total is still unknown, so no share can be shown. */
    data object Indeterminate : BackgroundOperationBarState

    /** An operation is running with a known total. */
    data class Determinate(val percent: Int) : BackgroundOperationBarState
}
