package com.sza.fastmediasorter.data.repository

import androidx.room.withTransaction
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.LauncherCellDao
import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.data.local.db.LauncherStateDao
import com.sza.fastmediasorter.data.local.db.LauncherStateEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
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

    // S2301: everything that reasons about a whole section block lives in its own class - see
    // [LauncherSectionCoordinator]; the contract's section members delegate to it unchanged.
    private val sectionOps = LauncherSectionCoordinator(db, cellDao)

    override fun observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>> =
        cellDao.observeByOrientation(orientation.name)
            .map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
            .distinctUntilChanged()

    override suspend fun addCell(cell: LauncherCell, columns: Int): LauncherCellPlacement =
        withContext(Dispatchers.IO) {
            val candidate = cell.normalized()
            // Nothing can seat a footprint wider than the grid, so this is decided before any writing.
            if (columns >= MIN_SPAN && candidate.spanW > columns) {
                Timber.i(
                    "Launcher desktop: %d columns cannot hold a %d-wide cell",
                    columns,
                    candidate.spanW,
                )
                return@withContext LauncherCellPlacement.TooWide
            }
            // The push and the insert must be one transaction: a shift that landed without its cell would
            // leave a hole in the desktop, and two concurrent adds that each saw free space would both
            // land, making the "cells never overlap" invariant false forever after.
            db.withTransaction { seat(candidate) }
        }

    /**
     * S1772: clears the candidate's footprint by pushing the desktop's tail down, then writes it.
     *
     * A section header never pushes: a second header on an occupied row is a duplicate, not a widget
     * needing room, and moving the first one aside would leave the desktop with two of them.
     */
    private suspend fun seat(candidate: LauncherCell): LauncherCellPlacement {
        val blocked = isBlocked(candidate)
        if (blocked && candidate.kind == LauncherCellKind.SECTION) {
            Timber.i("Launcher desktop: not adding a header at %d - that row already carries one", candidate.rowIndex)
            return LauncherCellPlacement.Refused
        }
        if (blocked) {
            pushTailDown(candidate)
        }
        return LauncherCellPlacement.Placed(cellDao.upsert(candidate.toEntity()))
    }

    private suspend fun isBlocked(candidate: LauncherCell): Boolean {
        val straddlesHeader = coversHeaderRow(
            kindName = candidate.kind.name,
            rowIndex = candidate.rowIndex,
            spanH = candidate.spanH,
            orientationName = candidate.orientation.name,
            screenIndex = candidate.screenIndex,
        )
        val blocker = cellDao.findOverlapping(
            orientation = candidate.orientation.name,
            screenIndex = candidate.screenIndex,
            rowIndex = candidate.rowIndex,
            colIndex = candidate.colIndex,
            spanW = candidate.spanW,
            spanH = candidate.spanH,
            excludeId = candidate.id,
        )
        return straddlesHeader || blocker != null
    }

    /**
     * Shifts every row from the first one in the way down far enough to clear the whole footprint.
     *
     * The push starts at the topmost row any obstruction *begins* on, not at the candidate's own anchor:
     * a tall cell that started higher up and reaches down into the footprint would survive a push that
     * only moved rows at or below the anchor, and would still be in the way. [delta] is then measured
     * from that row, so everything moved ends up below the footprint's last row.
     */
    private suspend fun pushTailDown(candidate: LauncherCell) {
        val band = cellDao.findInRowBand(
            orientation = candidate.orientation.name,
            screenIndex = candidate.screenIndex,
            fromRow = candidate.rowIndex,
            spanH = candidate.spanH,
        )
        val fromRow = (band.minOfOrNull { it.rowIndex } ?: candidate.rowIndex)
            .coerceAtMost(candidate.rowIndex)
        val delta = candidate.rowIndex + candidate.spanH - fromRow
        Timber.i(
            "Launcher desktop: pushing rows from %d down by %d to seat a %dx%d cell at %d,%d",
            fromRow,
            delta,
            candidate.spanW,
            candidate.spanH,
            candidate.rowIndex,
            candidate.colIndex,
        )
        cellDao.pushRowsDown(
            orientation = candidate.orientation.name,
            screenIndex = candidate.screenIndex,
            fromRow = fromRow,
            delta = delta,
        )
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

    override suspend fun addCellInSection(cell: LauncherCell, columns: Int, sectionKey: String): Long? =
        withContext(Dispatchers.IO) {
            if (columns < MIN_SPAN) {
                Timber.w("Launcher desktop: cannot place a cell on a %d-column grid", columns)
                return@withContext null
            }
            val normalized = cell.normalized()
            val candidate = normalized.copy(spanW = normalized.spanW.coerceAtMost(columns))
            db.withTransaction {
                val header = findSectionHeader(candidate.orientation, candidate.screenIndex, sectionKey)
                    ?: return@withTransaction null
                // The real header rows, not [headerRowsFor]: that one answers the straddle rule and is
                // empty for anything but a tall gadget, which would make every section look like it runs
                // to the bottom of the desktop and defeat the bounding this placement is built on.
                // Harmless for the straddle check itself, which is false for any single-row cell.
                val headerRows = cellDao.sectionHeaderRows(
                    orientation = candidate.orientation.name,
                    screenIndex = candidate.screenIndex,
                    kind = LauncherCellKind.SECTION.name,
                )
                val scanSpanW = candidate.spanW.coerceAtMost(columns)
                val lastCol = columns - scanSpanW
                val anchor = findSectionFreeAnchor(
                    candidate = candidate,
                    header = header,
                    scanSpanW = scanSpanW,
                    lastCol = lastCol,
                    headerRows = headerRows,
                ) ?: growSection(candidate, header, headerRows)
                cellDao.upsert(candidate.copy(rowIndex = anchor.row, colIndex = anchor.col).toEntity())
            }
        }

    /**
     * S2018: opens a row at the end of [header]'s section by moving everything below it down.
     *
     * The alternative - giving up and letting the caller scan the whole grid - is the defect this
     * exists to remove: an import of a hundred apps then settles into whichever neighbouring section
     * still had a gap. A contiguous tail push keeps every other cell's section membership intact,
     * because that membership is positional and a selective shift would re-parent cells.
     */
    private suspend fun growSection(
        candidate: LauncherCell,
        header: LauncherCell,
        headerRows: List<Int>,
    ): GridAnchor {
        val startRow = header.rowIndex.coerceAtLeast(0)
        // Null means this section runs to the bottom, so the empty band under the desktop is its end.
        val insertRow = LauncherSectionMembership.sectionEndExclusive(startRow, headerRows)
            ?: cellDao.firstRowBelowAll(candidate.orientation.name, candidate.screenIndex)
        cellDao.pushRowsDown(
            candidate.orientation.name,
            candidate.screenIndex,
            insertRow,
            candidate.spanH,
        )
        return GridAnchor(insertRow, 0)
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
        val lastRow = cellDao.firstRowBelowAll(candidate.orientation.name, candidate.screenIndex)
        val headerRows = headerRowsFor(
            kindName = candidate.kind.name,
            spanH = candidate.spanH,
            orientationName = candidate.orientation.name,
            screenIndex = candidate.screenIndex,
        )
        val scanSpanW = candidate.spanW.coerceAtMost(columns)
        val lastCol = if (candidate.kind == LauncherCellKind.SECTION) 0 else columns - scanSpanW

        // S1760: a resource shortcut lands in SECTION_RESOURCES first if the section exists and has room
        if (candidate.kind == LauncherCellKind.SHORTCUT &&
            candidate.target.startsWith(LauncherCellCommand.PREFIX_RESOURCE)
        ) {
            Timber.d("S2057: resource shortcut section-bounded scan entered")
            val header = findSectionHeader(
                orientation = candidate.orientation,
                screenIndex = candidate.screenIndex,
                sectionKey = LauncherCellCommand.SECTION_RESOURCES,
            )
            val sectionAnchor = header?.let {
                // S2057: headerRowsFor answers the straddle rule and is empty for non-gadgets,
                // which would make sectionEndExclusive return null and the scan unbounded.
                // Use the real section header rows, as addCellInSection does.
                val sectionHeaders = cellDao.sectionHeaderRows(
                    orientation = candidate.orientation.name,
                    screenIndex = candidate.screenIndex,
                    kind = LauncherCellKind.SECTION.name,
                )
                findSectionFreeAnchor(
                    candidate = candidate,
                    header = it,
                    scanSpanW = scanSpanW,
                    lastCol = lastCol,
                    headerRows = sectionHeaders,
                )
            }
            if (sectionAnchor != null) return sectionAnchor
        }

        return scanRows(
            rows = 0..lastRow,
            candidate = candidate,
            scanSpanW = scanSpanW,
            headerRows = headerRows,
            firstColOfRow = { 0 },
            lastCol = lastCol,
        )
    }

    /**
     * The row-major probe both anchor scans share: the first row-and-column whose whole footprint is
     * clear, or null when the range holds none.
     *
     * Extracted from the two callers rather than repeated: they differ only in which rows they walk and
     * where each row starts, and each had its own copy of the same nested loop.
     */
    private suspend fun scanRows(
        rows: IntRange,
        candidate: LauncherCell,
        scanSpanW: Int,
        headerRows: List<Int>,
        firstColOfRow: (Int) -> Int,
        lastCol: Int,
    ): GridAnchor? {
        for (row in rows) {
            if (LauncherSectionMembership.coversHeaderRow(row, candidate.spanH, headerRows)) continue
            for (col in firstColOfRow(row)..lastCol) {
                val blocker = cellDao.findOverlapping(
                    orientation = candidate.orientation.name,
                    screenIndex = candidate.screenIndex,
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

    /**
     * S1760: the header cell of section [sectionKey] on [orientation], or null when it has none.
     *
     * Three target shapes are accepted because a section's identity is its encoded target and older
     * desktops stored it less strictly than the encoder writes it today.
     */
    private suspend fun findSectionHeader(
        orientation: LauncherOrientation,
        screenIndex: Int,
        sectionKey: String,
    ): LauncherCell? {
        val sectionHeaderTarget = LauncherCellCommand.Section(sectionKey).encode()
        return cellDao.sectionHeaders(orientation.name, screenIndex, LauncherCellKind.SECTION.name)
            .mapNotNull { it.toDomainOrNull() }
            .firstOrNull {
                it.target == sectionHeaderTarget ||
                    it.target.endsWith(":$sectionKey") ||
                    it.target == sectionKey
            }
    }

    /**
     * S1760: scans for a free slot inside the section headed by [header], starting at the section's
     * header row down to the start of the next section.
     */
    private suspend fun findSectionFreeAnchor(
        candidate: LauncherCell,
        header: LauncherCell,
        scanSpanW: Int,
        lastCol: Int,
        headerRows: List<Int>,
    ): GridAnchor? {
        val startRow = header.rowIndex.coerceAtLeast(0)
        val endRowExclusive = LauncherSectionMembership.sectionEndExclusive(startRow, headerRows)
        val maxRow = endRowExclusive?.minus(1)
            ?: cellDao.firstRowBelowAll(candidate.orientation.name, candidate.screenIndex)

        // The header owns the head of its own row, so content on that row starts past it.
        return scanRows(
            rows = startRow..maxRow,
            candidate = candidate,
            scanSpanW = scanSpanW,
            headerRows = headerRows,
            firstColOfRow = { row -> if (row == startRow) LauncherSectionMembership.HEADER_SPAN_W else 0 },
            lastCol = lastCol,
        )
    }

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
        screenIndex: Int,
    ): Boolean = LauncherSectionMembership.coversHeaderRow(
        row = rowIndex,
        spanH = spanH,
        headerRows = headerRowsFor(kindName, spanH, orientationName, screenIndex),
    )

    /**
     * The header rows the straddle rule must respect, or an empty list when the rule cannot apply.
     *
     * Both cheap exits skip the query rather than the comparison: only a gadget is subject to the rule
     * and only a cell taller than one row can span a boundary, so every shortcut placement - the common
     * case by far - costs no round trip at all.
     */
    private suspend fun headerRowsFor(
        kindName: String,
        spanH: Int,
        orientationName: String,
        screenIndex: Int,
    ): List<Int> {
        if (kindName != LauncherCellKind.GADGET.name || spanH <= MIN_STRADDLING_SPAN_H) return emptyList()
        return cellDao.sectionHeaderRows(orientationName, screenIndex, LauncherCellKind.SECTION.name)
    }

    override suspend fun removeCell(id: Long) = withContext(Dispatchers.IO) {
        cellDao.deleteById(id)
    }

    override suspend fun moveCellToScreen(
        orientation: LauncherOrientation,
        cellId: Long,
        screenIndex: Int,
        columns: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        if (columns < MIN_SPAN || screenIndex < 0) {
            Timber.w(
                "Launcher desktop: cannot move cell %d to screen %d on a %d-column grid",
                cellId,
                screenIndex,
                columns,
            )
            return@withContext false
        }
        // One transaction for the same reason a placement is one: a block half-written across two
        // screens is an arrangement no rule can restore, and a reader between the two writes would see
        // the section without its header.
        db.withTransaction {
            val source = cellDao.getById(cellId) ?: return@withTransaction false
            if (source.orientation != orientation.name || source.screenIndex == screenIndex) {
                return@withTransaction false
            }
            if (source.kind == SECTION_KIND) {
                sectionOps.moveSectionBlockToScreen(orientation, source, screenIndex)
            } else {
                moveSingleCellToScreen(source, screenIndex, columns)
            }
        }
    }

    /** Seats one cell on the target screen's first free anchor and writes both changes together. */
    private suspend fun moveSingleCellToScreen(
        source: LauncherCellEntity,
        screenIndex: Int,
        columns: Int,
    ): Boolean {
        val anchor = source.toDomainOrNull()
            ?.copy(screenIndex = screenIndex)
            ?.let { findFreeAnchor(it, columns) }
            ?: return false
        cellDao.update(
            source.copy(screenIndex = screenIndex, rowIndex = anchor.row, colIndex = anchor.col),
        )
        Timber.i(
            "Launcher desktop: moved cell %d to screen %d at %d,%d",
            source.id,
            screenIndex,
            anchor.row,
            anchor.col,
        )
        return true
    }

    override suspend fun normalizeSectionSpans() {
        withContext(Dispatchers.IO) {
            val narrowed = cellDao.narrowSectionSpans(SECTION_KIND, LauncherSectionMembership.HEADER_SPAN_W)
            if (narrowed > 0) {
                Timber.i("Launcher desktop: narrowed %d section header(s) to the compact span", narrowed)
            }
        }
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
                    screenIndex = source.screenIndex,
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
                    screenIndex = source.screenIndex,
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

    override suspend fun updateCellLabel(id: Long, labelOverride: String?): Boolean =
        withContext(Dispatchers.IO) {
            val source = cellDao.getById(id) ?: return@withContext false
            if (source.labelOverride == labelOverride) return@withContext false
            cellDao.update(source.copy(labelOverride = labelOverride))
            true
        }

    override suspend fun swapSectionBlock(
        orientation: LauncherOrientation,
        sectionCellId: Long,
        moveUp: Boolean,
    ): Boolean = sectionOps.swapSectionBlock(orientation, sectionCellId, moveUp)

    override suspend fun removeSection(
        orientation: LauncherOrientation,
        sectionCellId: Long,
    ): List<String> = sectionOps.removeSection(orientation, sectionCellId)

    override suspend fun resortSection(
        orientation: LauncherOrientation,
        sectionCellId: Long,
        columns: Int,
    ): Boolean = sectionOps.resortSection(orientation, sectionCellId, columns)

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
                    screenIndex = source.screenIndex,
                )
                if (straddlesHeader) return@withTransaction false
                val blocker = cellDao.findOverlapping(
                    orientation = source.orientation,
                    screenIndex = source.screenIndex,
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

    override suspend fun clearAll(): List<String> = withContext(Dispatchers.IO) {
        // One transaction: a crash between the two deletes would otherwise leave an empty desktop
        // still flagged as seeded, and the starter set would never come back. S2217: the read shares
        // that transaction so no cell can slip in between being reported as deleted and vanishing -
        // its instance data would then survive the reset with nothing left pointing at it.
        db.withTransaction {
            val targets = cellDao.getAllCellsSync().map { it.target }
            cellDao.deleteAll()
            stateDao.deleteAll()
            targets
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

    /** A free square the scan settled on: where a cell's footprint starts. */
    private data class GridAnchor(val row: Int, val col: Int)

    private companion object {

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
