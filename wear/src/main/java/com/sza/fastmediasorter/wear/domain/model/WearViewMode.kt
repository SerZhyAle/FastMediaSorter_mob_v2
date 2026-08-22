package com.sza.fastmediasorter.wear.domain.model

private const val SINGLE_COLUMN = 1
private const val TWO_COLUMNS = 2
private const val THREE_COLUMNS = 3

/**
 * View mode shared by the watch navigation screens - the home screen and the Resources page.
 *
 * [requestedColumns] is what the user asked for, not what a screen ends up drawing: a narrow round
 * display can refuse three columns because the resulting cell falls under the interactive target.
 * The actual count comes from GridColumnFit.
 */
enum class WearViewMode(val requestedColumns: Int) {
    LIST(SINGLE_COLUMN),
    GRID_2(TWO_COLUMNS),
    GRID_3(THREE_COLUMNS);

    companion object {
        /** Stored preferences predate this value, so an unknown or absent name keeps the previous list behaviour. */
        fun fromNameOrDefault(name: String?): WearViewMode =
            entries.firstOrNull { it.name == name } ?: LIST
    }
}
