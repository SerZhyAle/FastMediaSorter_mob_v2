package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * S0404: one item placed on the launcher desktop. [orientation] and [kind] are stored as enum name
 * strings, and [target] carries an encoded command, so new command or cell kinds never force a
 * schema migration.
 */
@Entity(tableName = "launcher_cells")
data class LauncherCellEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orientation: String,
    val rowIndex: Int,
    val colIndex: Int,
    val spanW: Int,
    val spanH: Int,
    val kind: String,
    val target: String,
    val labelOverride: String?,
    val addedAt: Long,
    val screenIndex: Int = 0
)

@Dao
interface LauncherCellDao {

    @Query("SELECT * FROM launcher_cells WHERE orientation = :orientation ORDER BY rowIndex ASC, colIndex ASC")
    fun observeByOrientation(orientation: String): Flow<List<LauncherCellEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LauncherCellEntity): Long

    @Update
    suspend fun update(entity: LauncherCellEntity)

    @Query("DELETE FROM launcher_cells WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM launcher_cells")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LauncherCellEntity>)

    @Query("SELECT COUNT(*) FROM launcher_cells WHERE orientation = :orientation")
    suspend fun countByOrientation(orientation: String): Int

    @Query("SELECT * FROM launcher_cells WHERE id = :id")
    suspend fun getById(id: Long): LauncherCellEntity?

    @Query("SELECT * FROM launcher_cells ORDER BY rowIndex ASC, colIndex ASC")
    suspend fun getAllCellsSync(): List<LauncherCellEntity>

    /**
     * S2018: the section headers of one orientation, in reading order.
     *
     * Narrow on purpose. Locating a section used to go through [getAllCellsSync], which a bulk import
     * calls once per placed cell - so the read grew with the very import that was doing the asking,
     * while the answer only ever comes from the handful of rows this returns.
     */
    @Query(
        "SELECT * FROM launcher_cells WHERE orientation = :orientation AND kind = :kind " +
            "ORDER BY rowIndex ASC, colIndex ASC"
    )
    suspend fun sectionHeaders(orientation: String, kind: String): List<LauncherCellEntity>

    /**
     * The first row index strictly below every cell in [orientation] - i.e. the top of the empty band
     * under the desktop, and 0 when the desktop is empty. A free-slot scan uses it as its upper bound:
     * that row overlaps nothing by construction, so the search is guaranteed to terminate there.
     */
    @Query("SELECT COALESCE(MAX(rowIndex + spanH), 0) FROM launcher_cells WHERE orientation = :orientation")
    suspend fun firstRowBelowAll(orientation: String): Int

    /**
     * S1428: the rows carrying a section header, ascending. [kind] is passed rather than written into
     * the SQL so the enum name has one home in Kotlin.
     *
     * A section boundary is horizontal - a header spans its whole row - so the row alone answers which
     * section owns a cell, and the placement layer never needs the headers' columns.
     */
    @Query(
        "SELECT DISTINCT rowIndex FROM launcher_cells WHERE orientation = :orientation " +
            "AND kind = :kind ORDER BY rowIndex ASC"
    )
    suspend fun sectionHeaderRows(orientation: String, kind: String): List<Int>

    /**
     * S1642: narrows every stored section header to [spanW], returning how many rows changed.
     *
     * A desktop written by an S1428 build stored its headers at the widest grid there is, so the squares
     * beside a header read as occupied in the table while the renderer now draws it two cells wide - the
     * false occupancy this ticket exists to remove, and the one thing narrowing at render time cannot fix,
     * because [findOverlapping] queries the stored span. Idempotent by the `spanW != :spanW` clause, so it
     * can run on every launcher start and writes nothing once the desktop is already narrow.
     *
     * Both the kind and the target span are parameters, so the enum name and the span constant each keep
     * one home in Kotlin rather than gaining a second copy inside SQL.
     */
    @Query("UPDATE launcher_cells SET spanW = :spanW WHERE kind = :kind AND spanW != :spanW")
    suspend fun narrowSectionSpans(kind: String, spanW: Int): Int

    /**
     * The first cell whose footprint overlaps the rect at ([rowIndex], [colIndex]) sized
     * [spanW] x [spanH], ignoring [excludeId] (the cell being moved).
     *
     * Standard rect intersection, NOT an anchor match: a 2x2 gadget anchored at (0,0) also occupies
     * (0,1), (1,0) and (1,1), so a query keyed on the anchor alone reports those three as free and the
     * caller happily writes a cell on top of the gadget.
     */
    /**
     * S1772: moves the whole tail of a desktop down, so a widget can be seated where the user pointed.
     *
     * Every row at or below [fromRow] moves by the same [delta], which is what keeps section membership
     * intact: a section owns the cells below its header down to the next one, so a selective shift would
     * re-parent cells between sections. Shifting a contiguous tail cannot.
     */
    @Query(
        "UPDATE launcher_cells SET rowIndex = rowIndex + :delta " +
            "WHERE orientation = :orientation AND rowIndex >= :fromRow"
    )
    suspend fun pushRowsDown(orientation: String, fromRow: Int, delta: Int): Int

    /**
     * Every cell whose footprint reaches into rows [fromRow, fromRow + spanH), whatever column it sits in.
     *
     * Column-blind on purpose: the push moves whole rows, so what matters is which rows are involved, not
     * whether a particular square collides.
     */
    @Query(
        "SELECT * FROM launcher_cells WHERE orientation = :orientation " +
            "AND rowIndex < :fromRow + :spanH AND rowIndex + spanH > :fromRow"
    )
    suspend fun findInRowBand(orientation: String, fromRow: Int, spanH: Int): List<LauncherCellEntity>

    @Query(
        "SELECT * FROM launcher_cells WHERE orientation = :orientation AND id != :excludeId " +
            "AND :colIndex < colIndex + spanW AND colIndex < :colIndex + :spanW " +
            "AND :rowIndex < rowIndex + spanH AND rowIndex < :rowIndex + :spanH LIMIT 1"
    )
    suspend fun findOverlapping(
        orientation: String,
        rowIndex: Int,
        colIndex: Int,
        spanW: Int,
        spanH: Int,
        excludeId: Long,
    ): LauncherCellEntity?
}
