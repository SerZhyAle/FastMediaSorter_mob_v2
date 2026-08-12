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
    val addedAt: Long
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
     * The first cell whose footprint overlaps the rect at ([rowIndex], [colIndex]) sized
     * [spanW] x [spanH], ignoring [excludeId] (the cell being moved).
     *
     * Standard rect intersection, NOT an anchor match: a 2x2 gadget anchored at (0,0) also occupies
     * (0,1), (1,0) and (1,1), so a query keyed on the anchor alone reports those three as free and the
     * caller happily writes a cell on top of the gadget.
     */
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
