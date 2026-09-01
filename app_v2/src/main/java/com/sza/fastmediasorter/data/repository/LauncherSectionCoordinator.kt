package com.sza.fastmediasorter.data.repository

import androidx.room.withTransaction
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.LauncherCellDao
import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S2301: every desktop operation whose subject is a whole section block - swapping two, deleting one,
 * repacking one, moving one to another screen, and the row arithmetic all four share.
 *
 * Lifted out of [LauncherDesktopRepositoryImpl] unchanged, where they were the largest group of methods
 * that shared no state with the placement scan beside them: they all start by reading one screen's cells
 * and asking [LauncherSectionMembership] who owns what. The repository keeps the contract and delegates,
 * so no caller changed.
 *
 * Each public method owns its own transaction, exactly as it did on the repository - except
 * [moveSectionBlockToScreen], which runs inside the caller's.
 *
 * Named `Coordinator` because Rule 6's gate reads the suffix of every class under `data/repository`:
 * this is neither a repository nor a store, and it coordinates the four operations above over one
 * shared band arithmetic.
 */
internal class LauncherSectionCoordinator(
    private val db: AppDatabase,
    private val cellDao: LauncherCellDao,
) {

    suspend fun swapSectionBlock(
        orientation: LauncherOrientation,
        sectionCellId: Long,
        moveUp: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        db.withTransaction {
            val screenIndex = cellDao.getById(sectionCellId)?.screenIndex ?: return@withTransaction false
            val entities = cellsOfScreen(orientation, screenIndex)
            val cells = entities.mapNotNull { it.toDomainOrNull() }
            val sections = LauncherSectionMembership.sectionsInOrder(cells)
            val targetIndex = sections.indexOfFirst { it.id == sectionCellId }
            if (targetIndex == -1) return@withTransaction false

            val adjacentIndex = if (moveUp) targetIndex - 1 else targetIndex + 1
            if (adjacentIndex !in sections.indices) return@withTransaction false

            val sectionA = if (moveUp) sections[adjacentIndex] else sections[targetIndex]
            val sectionB = if (moveUp) sections[targetIndex] else sections[adjacentIndex]

            val blockAEntities = entities.filter { entity ->
                val domain = entity.toDomainOrNull() ?: return@filter false
                LauncherSectionMembership.ownerOf(domain, sections)?.id == sectionA.id
            }
            val blockBEntities = entities.filter { entity ->
                val domain = entity.toDomainOrNull() ?: return@filter false
                LauncherSectionMembership.ownerOf(domain, sections)?.id == sectionB.id
            }

            if (blockAEntities.isEmpty() || blockBEntities.isEmpty()) return@withTransaction false

            val minRowA = sectionA.rowIndex.coerceAtLeast(0)
            val minRowB = sectionB.rowIndex.coerceAtLeast(0)
            val maxRowBEnd = blockBEntities.maxOf { it.rowIndex + it.spanH }

            val heightA = minRowB - minRowA
            val heightB = maxRowBEnd - minRowB

            blockAEntities.forEach { entity ->
                cellDao.update(entity.copy(rowIndex = entity.rowIndex + heightB))
            }
            blockBEntities.forEach { entity ->
                cellDao.update(entity.copy(rowIndex = entity.rowIndex - heightA))
            }
            true
        }
    }

    suspend fun removeSection(
        orientation: LauncherOrientation,
        sectionCellId: Long,
    ): List<String> = withContext(Dispatchers.IO) {
        db.withTransaction {
            val screenIndex = cellDao.getById(sectionCellId)?.screenIndex
                ?: return@withTransaction emptyList()
            val entities = cellsOfScreen(orientation, screenIndex)
            val cells = entities.mapNotNull { it.toDomainOrNull() }
            val sections = LauncherSectionMembership.sectionsInOrder(cells)
            val header = sections.firstOrNull { it.id == sectionCellId }
                // An id that names no section header of this orientation deletes nothing.
                ?: return@withTransaction emptyList()

            val blockIds = entities.filter { entity ->
                val domain = entity.toDomainOrNull() ?: return@filter false
                LauncherSectionMembership.ownerOf(domain, sections)?.id == header.id
            }.map { it.id }.toSet()
            val headerRow = header.rowIndex.coerceAtLeast(0)
            val bandEnd = LauncherSectionMembership.sectionEndExclusive(
                headerRow,
                LauncherSectionMembership.headerRows(cells),
            ) ?: cellDao.firstRowBelowAll(orientation.name, screenIndex)

            // Targets come from the rows read inside this same transaction (S2217, ADR-3): the caller
            // clears stored configured-widget instances with them, so a target this call did not remove
            // would clear an instance a surviving cell still points at.
            val removedTargets = entities.filter { it.id in blockIds }.map { it.target }
            blockIds.forEach { cellDao.deleteById(it) }

            val remaining = cellsOfScreen(orientation, screenIndex)
            compactBandRows(remaining, headerRow, bandEnd, packedIds = emptySet())
            removedTargets
        }
    }

    suspend fun resortSection(
        orientation: LauncherOrientation,
        sectionCellId: Long,
        columns: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        if (columns < MIN_SPAN) {
            Timber.w("Launcher desktop: cannot repack a section on a %d-column grid", columns)
            return@withContext false
        }
        db.withTransaction {
            val screenIndex = cellDao.getById(sectionCellId)?.screenIndex ?: return@withTransaction false
            val entities = cellsOfScreen(orientation, screenIndex)
            val before = entities.associate { it.id to (it.rowIndex to it.colIndex) }
            val cells = entities.mapNotNull { it.toDomainOrNull() }
            val sections = LauncherSectionMembership.sectionsInOrder(cells)
            val header = sections.firstOrNull { it.id == sectionCellId }
                ?: return@withTransaction false
            val owned = cells.filter {
                it.id != header.id &&
                    LauncherSectionMembership.ownerOf(it, sections)?.id == header.id
            }
            if (owned.isEmpty()) return@withTransaction false

            val headerRow = header.rowIndex.coerceAtLeast(0)
            val bandEnd = LauncherSectionMembership.sectionEndExclusive(
                headerRow,
                LauncherSectionMembership.headerRows(cells),
            ) ?: cellDao.firstRowBelowAll(orientation.name, screenIndex)
            // Fixed obstacles at their stored positions: every co-section cell sharing the band (S1645).
            // The repack routes around them, never through them - they belong to another section.
            val obstacles = cells.filter {
                it.rowIndex >= headerRow && it.rowIndex < bandEnd &&
                    LauncherSectionMembership.ownerOf(it, sections)?.id != header.id
            }
            val ordered = owned.sortedWith(compareBy({ it.rowIndex }, { it.colIndex }))
            val packed = packSectionContents(ordered, header, obstacles, bandEnd, columns)
            packed.forEach { cellDao.update(it.toEntity()) }

            val remaining = cellsOfScreen(orientation, screenIndex)
            compactBandRows(remaining, headerRow, bandEnd, packedIds = packed.map { it.id }.toSet())

            val after = cellsOfScreen(orientation, screenIndex)
                .associate { it.id to (it.rowIndex to it.colIndex) }
            after != before
        }
    }

    /**
     * Moves a section header and every cell it owns onto [screenIndex], then closes the gap the block
     * left behind - the same band compaction [removeSection] runs, for the same reason: the rows under
     * the departed block would otherwise stay pushed down by a section that is no longer there.
     */
    suspend fun moveSectionBlockToScreen(
        orientation: LauncherOrientation,
        header: LauncherCellEntity,
        screenIndex: Int,
    ): Boolean {
        val sourceScreen = header.screenIndex
        val entities = cellsOfScreen(orientation, sourceScreen)
        val cells = entities.mapNotNull { it.toDomainOrNull() }
        val sections = LauncherSectionMembership.sectionsInOrder(cells)
        // A header id this screen does not carry owns nothing, which the empty-block check below
        // already refuses - so it needs no return of its own.
        val headerCell = sections.firstOrNull { it.id == header.id }
        val block = entities.filter { entity ->
            val domain = entity.toDomainOrNull() ?: return@filter false
            headerCell != null && LauncherSectionMembership.ownerOf(domain, sections)?.id == headerCell.id
        }
        if (headerCell == null || block.isEmpty()) return false

        val headerRow = headerCell.rowIndex.coerceAtLeast(0)
        val bandEnd = LauncherSectionMembership.sectionEndExclusive(
            headerRow,
            LauncherSectionMembership.headerRows(cells),
        ) ?: cellDao.firstRowBelowAll(orientation.name, sourceScreen)

        // Below everything already there: those rows are free by construction, so a block that kept its
        // internal geometry cannot land on anything, and no per-cell scan is needed.
        val landingRow = cellDao.firstRowBelowAll(orientation.name, screenIndex)
        block.forEach { entity ->
            cellDao.update(
                entity.copy(
                    screenIndex = screenIndex,
                    rowIndex = landingRow + (entity.rowIndex - headerRow),
                ),
            )
        }
        Timber.i(
            "Launcher desktop: moved section %d (%d cell(s)) to screen %d at row %d",
            header.id,
            block.size,
            screenIndex,
            landingRow,
        )
        compactBandRows(
            remaining = cellsOfScreen(orientation, sourceScreen),
            headerRow = headerRow,
            tailFromRow = bandEnd,
            packedIds = emptySet(),
        )
        return true
    }

    /**
     * S2301: the cells of one screen, which is the scope every block operation reasons in.
     *
     * A block operation walks section membership, which is positional - the cells below a header down to
     * the next one. Screens carry independent row coordinates, so a list spanning them would interleave
     * two screens' rows and hand a section the cells of its opposite number.
     */
    suspend fun cellsOfScreen(
        orientation: LauncherOrientation,
        screenIndex: Int,
    ): List<LauncherCellEntity> = cellDao.getAllCellsSync()
        .filter { it.orientation == orientation.name && it.screenIndex == screenIndex }

    /**
     * S2222: closes the row gap a block operation left in the band starting at [headerRow] (ADR-2).
     *
     * Rows are counted per covered row, never per anchor, so a multi-row cell stays whole: every row it
     * covers is occupied, and consecutive occupied rows map onto consecutive rows. The walk covers the
     * band's own rows plus whatever [packedIds] the operation placed past its end; rows from
     * [tailFromRow] down ride by the height difference instead - the same tail rule a section swap
     * applies, extended to a band a co-section cell may still occupy (S1645), which a whole-band shift
     * would fold into the gap and drag a live section up under its neighbour.
     */
    suspend fun compactBandRows(
        remaining: List<LauncherCellEntity>,
        headerRow: Int,
        tailFromRow: Int,
        packedIds: Set<Long>,
    ) {
        val bandCells = remaining.filter {
            (it.rowIndex >= headerRow && it.rowIndex < tailFromRow) || it.id in packedIds
        }
        val occupiedRows = bandCells
            .flatMap { it.rowIndex until it.rowIndex + it.spanH }
            .distinct()
            .sorted()
        val rowMap = occupiedRows.mapIndexed { index, row -> row to headerRow + index }.toMap()
        bandCells.forEach { entity ->
            val compacted = rowMap.getValue(entity.rowIndex)
            if (compacted != entity.rowIndex) {
                cellDao.update(entity.copy(rowIndex = compacted))
            }
        }
        val shift = (tailFromRow - headerRow) - occupiedRows.size
        if (shift != 0) {
            remaining.filter { it.rowIndex >= tailFromRow && it.id !in packedIds }.forEach { entity ->
                cellDao.update(entity.copy(rowIndex = entity.rowIndex - shift))
            }
        }
    }

    /**
     * S2222: first-fit row-major repack of [ordered] section content around [obstacles].
     *
     * The scan starts on the header's own row, past [LauncherSectionMembership.HEADER_SPAN_W], and at
     * column 0 of every row below. A footprint is coerced to the live [columns] for scanning only - the
     * stored span is never widened, the rule the free-slot placement already applies. A cell that starts
     * inside the band must end inside it: ending on the next section's first row is the straddle the
     * placement layer refuses everywhere else, and the tail shift is what keeps that true after the
     * pack, not the scan alone. A cell that no longer fits the band starts past [bandEnd] instead, on
     * rows the tail shift is about to open.
     */
    fun packSectionContents(
        ordered: List<LauncherCell>,
        header: LauncherCell,
        obstacles: List<LauncherCell>,
        bandEnd: Int,
        columns: Int,
    ): List<LauncherCell> {
        val footprints = mutableListOf(
            SectionFootprint(header.rowIndex, header.colIndex, header.spanW, header.spanH),
        )
        obstacles.forEach {
            footprints.add(SectionFootprint(it.rowIndex, it.colIndex, it.spanW, it.spanH))
        }
        val packed = mutableListOf<LauncherCell>()
        for (cell in ordered) {
            val scanSpanW = cell.spanW.coerceAtMost(columns)
            var row = header.rowIndex
            var placed = false
            while (!placed) {
                val firstCol = if (row == header.rowIndex) LauncherSectionMembership.HEADER_SPAN_W else 0
                var col = firstCol
                while (!placed && col + scanSpanW <= columns) {
                    val candidate = SectionFootprint(row, col, scanSpanW, cell.spanH)
                    val crossesBandEnd = row < bandEnd && row + cell.spanH > bandEnd
                    if (!crossesBandEnd && footprints.none { it.overlaps(candidate) }) {
                        footprints.add(candidate)
                        packed.add(cell.copy(rowIndex = row, colIndex = col))
                        placed = true
                    } else {
                        col++
                    }
                }
                row++
            }
        }
        return packed
    }

    /** A rectangle on the grid; the pack asks nothing of a cell but whether two of these overlap. */
    private data class SectionFootprint(val row: Int, val col: Int, val spanW: Int, val spanH: Int) {
        fun overlaps(other: SectionFootprint): Boolean = row < other.row + other.spanH &&
            other.row < row + spanH &&
            col < other.col + other.spanW &&
            other.col < col + spanW
    }
}
