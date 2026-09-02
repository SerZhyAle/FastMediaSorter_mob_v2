package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.LauncherCellDao
import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.data.local.db.LauncherStateEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.domain.repository.LauncherDesktopState
import timber.log.Timber

/**
 * A cell always covers at least its own square; see [normalized].
 *
 * Internal rather than private: the repository's own guards read it too - a grid narrower than one
 * column can seat nothing, which is the same floor stated once.
 */
internal const val MIN_SPAN = 1

/**
 * Forces a cell into the shape the overlap invariant assumes before it can ever reach the table.
 *
 * A zero or negative span is not merely odd - it makes the stored rectangle empty, and an empty
 * rectangle intersects nothing, so [LauncherCellDao.findOverlapping] would report that square free
 * forever and let a second cell land straight on top of it. The renderer, meanwhile, floors every
 * span at 1 for display, so the two would visibly overlap while the database swore they could not.
 * Negative indices split the same two views apart the same way. The grid's right edge is deliberately
 * NOT enforced here: the column count belongs to the current screen, not to the stored desktop.
 *
 * S1642: a section header is forced to column 0 of its own row at exactly
 * [LauncherSectionMembership.HEADER_SPAN_W] columns. The span is pinned because one number has to
 * describe a header everywhere - the renderer, the free-square sweep and this table - and the column
 * because a header opens its row: the section's own content fills the squares to its right (strategic
 * §2.2), and a header sitting anywhere else would leave a gap ahead of it that belongs to no one.
 */
internal fun LauncherCell.normalized(): LauncherCell {
    val isHeader = kind == LauncherCellKind.SECTION
    return copy(
        rowIndex = rowIndex.coerceAtLeast(0),
        colIndex = if (isHeader) 0 else colIndex.coerceAtLeast(0),
        spanW = if (isHeader) LauncherSectionMembership.HEADER_SPAN_W else spanW.coerceAtLeast(MIN_SPAN),
        spanH = spanH.coerceAtLeast(MIN_SPAN),
    )
}

internal fun LauncherCell.toEntity(): LauncherCellEntity = LauncherCellEntity(
    id = id,
    orientation = orientation.name,
    rowIndex = rowIndex,
    colIndex = colIndex,
    spanW = spanW,
    spanH = spanH,
    kind = kind.name,
    target = target,
    labelOverride = labelOverride,
    addedAt = addedAt,
    screenIndex = screenIndex,
)

/** A row written by a newer build (unknown orientation/kind) is skipped, never a crash. */
internal fun LauncherCellEntity.toDomainOrNull(): LauncherCell? {
    val orientationValue = LauncherOrientation.entries.firstOrNull { it.name == orientation }
    val kindValue = LauncherCellKind.entries.firstOrNull { it.name == kind }
    if (orientationValue == null || kindValue == null) {
        Timber.w("Launcher desktop: skipping cell %d with unknown orientation/kind", id)
        return null
    }
    return LauncherCell(
        id = id,
        orientation = orientationValue,
        rowIndex = rowIndex,
        colIndex = colIndex,
        spanW = spanW,
        spanH = spanH,
        kind = kindValue,
        target = target,
        labelOverride = labelOverride,
        addedAt = addedAt,
        screenIndex = screenIndex,
    )
}

internal fun LauncherStateEntity.toDomain(): LauncherDesktopState = LauncherDesktopState(
    seededPortrait = seededPortrait,
    seededLandscape = seededLandscape,
    columnsPortrait = columnsPortrait,
    columnsLandscape = columnsLandscape,
)
