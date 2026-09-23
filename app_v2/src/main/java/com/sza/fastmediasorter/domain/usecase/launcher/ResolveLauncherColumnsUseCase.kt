package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S2679: the grid width a background placement must use when the desktop has not stored one yet.
 *
 * The stored width is written by the surface that renders the desktop and, since S2679, by the seed
 * that lays it out - so a zero here means neither has happened for this orientation. Before this,
 * three use cases each answered that case with a constant of their own (4, 4 and 6), none of them
 * derived from the desktop: on the device that opened this ticket the landscape starter set occupied
 * columns 0..10, and a placement scanning four columns could not see seven of the section's occupied
 * ones, so a returning shortcut landed on a new row instead of the free square it came from.
 *
 * The cells themselves are the only source that answers in the right order of magnitude, so the width
 * is derived from the rightmost occupied column and floored at [MIN_DESKTOP_COLUMNS].
 *
 * Nothing is written back. The derived figure is a lower bound - the real grid may be wider than its
 * rightmost occupied column - and the stored width stays owned by the surface that measures the screen.
 */
class ResolveLauncherColumnsUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
) {

    suspend operator fun invoke(orientation: LauncherOrientation, storedColumns: Int): Int {
        if (storedColumns > 0) return storedColumns
        val occupiedColumns = desktop.observeCells(orientation).first()
            .maxOfOrNull { it.colIndex + it.spanW } ?: 0
        val derived = maxOf(occupiedColumns, MIN_DESKTOP_COLUMNS)
        return derived
    }

    private companion object {
        /** Floor for an empty desktop, where no cell can say how wide the grid is. */
        const val MIN_DESKTOP_COLUMNS = 4
    }
}
