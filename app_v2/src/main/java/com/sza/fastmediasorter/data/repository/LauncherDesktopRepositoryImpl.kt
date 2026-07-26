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
                    candidate.rowIndex, candidate.colIndex, blocker.id,
                )
                return@withTransaction null
            }
            cellDao.upsert(candidate.toEntity())
        }
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
                if (blocker != null) return@withTransaction false
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
            val targetCol = colIndex.coerceAtLeast(0)
            db.withTransaction {
                val source = cellDao.getById(id) ?: return@withTransaction false
                if (source.rowIndex == targetRow && source.colIndex == targetCol) {
                    return@withTransaction false
                }
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
     */
    private fun LauncherCell.normalized(): LauncherCell = copy(
        rowIndex = rowIndex.coerceAtLeast(0),
        colIndex = colIndex.coerceAtLeast(0),
        spanW = spanW.coerceAtLeast(MIN_SPAN),
        spanH = spanH.coerceAtLeast(MIN_SPAN),
    )

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

        val DEFAULT_STATE = LauncherStateEntity(
            id = 1,
            seededPortrait = false,
            seededLandscape = false,
            columnsPortrait = 0,
            columnsLandscape = 0,
        )
    }
}
