package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.Composable

/**
 * S1949: one settings control as it is handed to the row layout.
 *
 * [fullWidth] is declared by the caller and never derived from the touch-target rule. Readability
 * and touch target are different constraints - a label can be unreadable in a cell that is still
 * comfortably tappable - and [GridColumnFit] knows only the second one.
 *
 * [content] is told whether it ended up sharing its row, because an item marked narrow can still be
 * handed the whole width by the odd-group rule, and the narrow and full-width forms of a control are
 * different shapes rather than one shape at two sizes - see [WearSettingsToggleCell].
 */
data class WearSettingsItem(
    val fullWidth: Boolean = false,
    val content: @Composable (narrow: Boolean) -> Unit
)
