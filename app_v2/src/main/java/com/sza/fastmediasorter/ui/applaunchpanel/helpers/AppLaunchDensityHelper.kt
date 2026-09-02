package com.sza.fastmediasorter.ui.applaunchpanel.helpers

import android.util.DisplayMetrics
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerWindow

/**
 * Helper to calculate grid column span for the All Programs window.
 * Inherits density configuration from desktop settings / display metrics with a minimum bound of 3 columns.
 */
object AppLaunchDensityHelper {

    private const val MIN_GRID_SPAN = 3

    fun calculateSpanCount(metrics: DisplayMetrics, preferredColumns: Int? = null): Int {
        val baseColumns = preferredColumns ?: SearchableOptionPickerWindow.columnsFor(metrics)
        return maxOf(MIN_GRID_SPAN, baseColumns)
    }
}
