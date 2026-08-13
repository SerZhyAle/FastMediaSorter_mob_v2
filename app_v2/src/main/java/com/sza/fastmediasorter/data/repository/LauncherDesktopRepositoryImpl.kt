package com.sza.fastmediasorter.data.repository

import androidx.room.withTransaction
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.LauncherCellDao
import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.data.local.db.LauncherStateDao
import com.sza.fastmediasorter.data.local.db.LauncherStateEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class LauncherDesktopRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val cellDao: LauncherCellDao,
    private val stateDao: LauncherStateDao,
) : LauncherDesktopRepository {

    override fun observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>> =
        cellDao.observeByOrientation(orientation.name)
            .map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
            .distinctUntilChanged()

    override suspend fun addCell(cell: LauncherCell): Long? = withContext(Dispatchers.IO) {
        val candidate = cell.normalized()
        // Check and insert must be one transaction: two concurrent adds that each saw free space would
        // otherwise both land, and the "cells never overlap" invariant would be false forever after.
        db.withTransaction {
            val straddlesHeader = coversHeaderRow(
                kindName = candidate.kind.name,
                rowIndex = candidate.rowIndex,
                spanH = candidate.spanH,
                orientationName = candidate.orientation.name,
            )
            if (straddlesHeader) {
                Timber.i(
                    "Launcher desktop: not adding at %d - a gadget may not cover a section header row",
                    candidate.rowIndex,
                )
                return@withTransaction null
            }
            val blocker = cellDao.findOverlapping(
                orientation = candidate.orientation.name,
                rowIndex = candidate.rowIndex,
                colIndex = candidate.colIndex,
                spanW = candidate.spanW,
                spanH = candidate.spanH,
                excludeId = candidate.id,
            )
            if (blocker != null) {
                Timber.i(
                    "Launcher desktop: not adding at %d,%d - cell %d already covers it",
                    candidate.rowIndex,
                    candidate.colIndex,
                    blocker.id,
                )
                return@withTransaction null
            }
            cellDao.upsert(candidate.toEntity())
        }
    }

    override suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long? =
        withContext(Dispatchers.IO) {
            if (columns < MIN_SPAN) {
                Timber.w("Launcher desktop: cannot place a cell on a %d-column grid", columns)
                return@withContext null
            }
            // Widen-past-the-edge is the one span correction the stored desktop may not keep: unlike a
            // row or column index, a footprint wider than the screen can never be rendered anywhere.
            val normalized = cell.normalized()
            // A header is exempt: [normalized] stores it at the widest grid it can ever be drawn on, and
            // narrowing it to this one screen would put the rest of its row back on the free list.
            val candidate = if (normalized.kind == LauncherCellKind.SECTION) {
                normalized
            } else {
                normalized.copy(spanW = normalized.spanW.coerceAtMost(columns))
            }
            // Scan and insert share one transaction for the same reason addCell's check does: two
            // concurrent placements that both saw the same square free would otherwise both land on it.
            db.withTransaction {
                val anchor = findFreeAnchor(candidate, columns) ?: return@withTransaction null
                Timber.i("Launcher desktop: placing new cell at %d,%d", anchor.row, anchor.col)
                cellDao.upsert(candidate.copy(rowIndex = anchor.row, colIndex = anchor.col).toEntity())
            }
        }

    /**
     * Row-major scan for the first anchor whose whole footprint is clear (owner decision, strategic §4
     * item 2).
     *
     * The upper bound is the empty band under the desktop rather than an arbitrary ceiling: that row
     * overlaps nothing by construction and every column in it is free, so a candidate that fits the grid
     * width at all is guaranteed to be placed there at the latest. That makes "append a new row below the
     * last occupied one" fall out of the same loop instead of needing a second code path, and it makes an
     * empty desktop terminate on the very first probe.
     */
    private suspend fun findFreeAnchor(candidate: LauncherCell, columns: Int): GridAnchor? {
        val lastRow = cellDao.firstRowBelowAll(candidate.orientation.name)
        // Read once, not once per row: the header rows cannot change while this scan runs - it is
        // inside the same transaction as the insert that follows - so querying them in the loop would
        // add one round trip per probed row for no new information.
        val headerRows = headerRowsFor(candidate.kind.name, candidate.spanH, candidate.orientation.name)
        // A header carries the span of the widest grid, not of this one, so the scan uses the width it
        // actually occupies here - the stored span would make the column range below empty and place
        // nothing at all.
        val scanSpanW = candidate.spanW.coerceAtMost(columns)
        for (row in 0..lastRow) {
            // S1428: a row a gadget may not straddle is skipped rather than refused, so a tall gadget
            // still lands further down instead of becoming unplaceable because the first free anchor
            // happened to sit just above a header.
            if (LauncherSectionMembership.coversHeaderRow(row, candidate.spanH, headerRows)) continue
            for (col in 0..columns - scanSpanW) {
                val blocker = cellDao.findOverlapping(
                    orientation = candidate.orientation.name,
                    rowIndex = row,
                    colIndex = col,
                    spanW = scanSpanW,
                    spanH = candidate.spanH,
                    excludeId = candidate.id,
                )
                if (blocker == null) return GridAnchor(row, col)
            }
        }
        return null
    }

    private data class GridAnchor(val row: Int, val col: Int)

    /**
     * S1428, strategic §6.11 as refined by the owner on 2026-08-08: a gadget may not cover a section
     * header row.
     *
     * That is the one placement whose section is undefined - a cell taller than one row starting inside
     * one section and ending inside the next belongs to neither. Only a gadget is ever taller than one
     * row, so a shortcut pays nothing for the rule.
     *
     * The earlier literal reading, "no gadget inside a section", stopped being implementable once §6.12
     * put a second header on the desktop: every row then lies inside one section or the other, so it
     * would have refused every gadget everywhere, the seeded clock included.
     *
     * This is deliberately not folded into [LauncherCellDao.findOverlapping]. That predicate reads the
     * **stored** span, while the renderer widens a header to the **live** column count, so after a
     * density change or a rotation onto a wider grid the squares past a header's stored span are free in
     * the database and covered on screen. This check is what still refuses them.
     */
    private suspend fun coversHeaderRow(
        kindName: String,
        rowIndex: Int,
        spanH: Int,
        orientationName: String,
    ): Boolean = LauncherSectionMembership.coversHeaderRow(
        row = rowIndex,
        spanH = spanH,
        headerRows = headerRowsFor(kindName, spanH, orientationName),
    ).also { covers ->
    }

    /**
     * The header rows the straddle rule must respect, or an empty list when the rule cannot apply.
     *
     * Both cheap exits skip the query rather than the comparison: only a gadget is subject to the rule
     * and only a cell taller than one row can span a boundary, so every shortcut placement - the common
     * case by far - costs no round trip at all.
     */
    private suspend fun headerRowsFor(kindName: String, spanH: Int, orientationName: String): List<Int> {
        if (kindName != LauncherCellKind.GADGET.name || spanH <= MIN_STRADDLING_SPAN_H) return emptyList()
        return cellDao.sectionHeaderRows(orientationName, LauncherCellKind.SECTION.name)
    }

    override suspend fun removeCell(id: Long) = withContext(Dispatchers.IO) {
        cellDao.deleteById(id)
    }

    override suspend fun resizeCell(id: Long, spanW: Int, spanH: Int): Boolean =
        withContext(Dispatchers.IO) {
            val safeW = spanW.coerceAtLeast(MIN_SPAN)
            val safeH = spanH.coerceAtLeast(MIN_SPAN)
            db.withTransaction {
                val source = cellDao.getById(id) ?: return@withTransaction false
                if (source.spanW == safeW && source.spanH == safeH) return@withTransaction false
                // Self is excluded, so growing over the cell's own current squares is fine; only another
                // cell's squares block the resize, keeping the "cells never overlap" invariant.
                val blocker = cellDao.findOverlapping(
                    orientation = source.orientation,
                    rowIndex = source.rowIndex,
                    colIndex = source.colIndex,
                    spanW = safeW,
                    spanH = safeH,
                    excludeId = id,
                )
                // S1428: growing downward is the other way a gadget reaches a header row, so the same
                // refusal that guards a move has to guard a resize - a rule enforced on one but not the
                // other is not enforced.
                val straddlesHeader = coversHeaderRow(
                    kindName = source.kind,
                    rowIndex = source.rowIndex,
                    spanH = safeH,
                    orientationName = source.orientation,
                )
                if (blocker != null || straddlesHeader) return@withTransaction false
                cellDao.update(source.copy(spanW = safeW, spanH = safeH))
                true
            }
        }

    override suspend fun updateCellTarget(id: Long, target: String): Boolean =
        withContext(Dispatchers.IO) {
            val source = cellDao.getById(id) ?: return@withContext false
            if (source.target == target) return@withContext false
            cellDao.update(source.copy(target = target))
            true
        }

    override suspend fun moveCell(id: Long, rowIndex: Int, colIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            val targetRow = rowIndex.coerceAtLeast(0)
            db.withTransaction {
                val source = cellDao.getById(id) ?: return@withTransaction false
                // S1428: a header stays anchored at column 0 for the same reason normalized() puts it
                // there - it is drawn across the whole row whatever column it was stored in, so any
                // other value frees squares in the table that stay covered on screen.
                val targetCol = if (source.kind == SECTION_KIND) 0 else colIndex.coerceAtLeast(0)
                if (source.rowIndex == targetRow && source.colIndex == targetCol) {
                    return@withTransaction false
                }
                // S1428: refused before the overlap lookup so the rule holds on both outcomes below -
                // the plain move and the equal-footprint swap, which would otherwise carry a gadget onto
                // a header row through the exchange path.
                val straddlesHeader = coversHeaderRow(
                    kindName = source.kind,
                    rowIndex = targetRow,
                    spanH = source.spanH,
                    orientationName = source.orientation,
                )
                if (straddlesHeader) return@withTransaction false
                val blocker = cellDao.findOverlapping(
                    orientation = source.orientation,
                    rowIndex = targetRow,
                    colIndex = targetCol,
                    spanW = source.spanW,
                    spanH = source.spanH,
                    excludeId = id,
                )
                if (blocker == null) {
                    cellDao.update(source.copy(rowIndex = targetRow, colIndex = targetCol))
                    return@withTransaction true
                }
                if (blocker.spanW != source.spanW || blocker.spanH != source.spanH) {
                    // A 2x2 cannot take a 1x1's place: it would still cover that 1x1's neighbours,
                    // trading one overlap for another. Only equal footprints have a defined exchange.
                    return@withTransaction false
                }
                swapAnchors(source, blocker)
                true
            }
        }

    /**
     * Trades two equal-footprint cells' positions (owner decision 2026-07-17 - the gesture every
     * Android launcher answers this way).
     *
     * Each cell is placed on the rectangle the other one already held, NOT on the drop point. That is
     * what makes the exchange collision-free without a second lookup: both rectangles were, by the
     * standing invariant, already free of every other cell, and they are the same size. Honouring the
     * finger's exact square instead could shove the pair onto a third cell, since a drop anywhere
     * inside a multi-square blocker is off-anchor.
     */
    private suspend fun swapAnchors(source: LauncherCellEntity, blocker: LauncherCellEntity) {
        cellDao.update(source.copy(rowIndex = blocker.rowIndex, colIndex = blocker.colIndex))
        cellDao.update(blocker.copy(rowIndex = source.rowIndex, colIndex = source.colIndex))
    }

    override suspend fun seedIfEmpty(
        orientation: LauncherOrientation,
        cells: List<LauncherCell>,
    ): Boolean = withContext(Dispatchers.IO) {
        db.withTransaction {
            val state = stateDao.get() ?: DEFAULT_STATE
            val alreadySeeded = when (orientation) {
                LauncherOrientation.PORTRAIT -> state.seededPortrait
                LauncherOrientation.LANDSCAPE -> state.seededLandscape
            }
            if (alreadySeeded || cellDao.countByOrientation(orientation.name) > 0) {
                return@withTransaction false
            }
            cellDao.insertAll(cells.map { it.toEntity() })
            val seeded = when (orientation) {
                LauncherOrientation.PORTRAIT -> state.copy(seededPortrait = true)
                LauncherOrientation.LANDSCAPE -> state.copy(seededLandscape = true)
            }
            stateDao.upsert(seeded)
            true
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            // One transaction: a crash between the two deletes would otherwise leave an empty desktop
            // still flagged as seeded, and the starter set would never come back.
            db.withTransaction {
                cellDao.deleteAll()
                stateDao.deleteAll()
            }
        }
    }

    override suspend fun state(): LauncherDesktopState = withContext(Dispatchers.IO) {
        (stateDao.get() ?: DEFAULT_STATE).toDomain()
    }

    override suspend fun updateColumns(orientation: LauncherOrientation, columns: Int) {
        withContext(Dispatchers.IO) {
            val current = stateDao.get() ?: DEFAULT_STATE
            val updated = when (orientation) {
                LauncherOrientation.PORTRAIT -> current.copy(columnsPortrait = columns)
                LauncherOrientation.LANDSCAPE -> current.copy(columnsLandscape = columns)
            }
            if (updated != current) stateDao.upsert(updated)
        }
    }

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
     * S1428: a section header is forced to column 0 at the full stored width for that same reason. The
     * renderer draws it across the live column count wherever it was stored, so any other anchor or span
     * leaves squares free in the table that are covered on screen - and a cell dropped on one of them
     * lands underneath the header.
     */
    private fun LauncherCell.normalized(): LauncherCell {
        val isHeader = kind == LauncherCellKind.SECTION
        return copy(
            rowIndex = rowIndex.coerceAtLeast(0),
            colIndex = if (isHeader) 0 else colIndex.coerceAtLeast(0),
            spanW = if (isHeader) LauncherSectionMembership.HEADER_STORED_SPAN_W else spanW.coerceAtLeast(MIN_SPAN),
            spanH = spanH.coerceAtLeast(MIN_SPAN),
        )
    }

    private fun LauncherCell.toEntity(): LauncherCellEntity = LauncherCellEntity(
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
    )

    /** A row written by a newer build (unknown orientation/kind) is skipped, never a crash. */
    private fun LauncherCellEntity.toDomainOrNull(): LauncherCell? {
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
        )
    }

    private fun LauncherStateEntity.toDomain(): LauncherDesktopState = LauncherDesktopState(
        seededPortrait = seededPortrait,
        seededLandscape = seededLandscape,
        columnsPortrait = columnsPortrait,
        columnsLandscape = columnsLandscape,
    )

    private companion object {
        /** A cell always covers at least its own square; see [normalized]. */
        const val MIN_SPAN = 1

        /** A one-row cell cannot span a boundary, so it can never straddle a header; see [coversHeaderRow]. */
        const val MIN_STRADDLING_SPAN_H = 1

        /** Entities store the kind as its enum name; compared as a string to avoid re-parsing a row. */
        val SECTION_KIND = LauncherCellKind.SECTION.name

        val DEFAULT_STATE = LauncherStateEntity(
            id = 1,
            seededPortrait = false,
            seededLandscape = false,
            columnsPortrait = 0,
            columnsLandscape = 0,
        )
    }
}
