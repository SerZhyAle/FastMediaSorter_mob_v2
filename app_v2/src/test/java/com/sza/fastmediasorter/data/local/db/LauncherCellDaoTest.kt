package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S0404: in-memory Room coverage for [LauncherCellDao.findOverlapping] - the rectangle-intersection
 * test the desktop's "cells never overlap" invariant rests on.
 *
 * Worth its own file because the query is pure geometry expressed in SQL, where an off-by-one reads as
 * plausible either way: `:colIndex < colIndex + spanW` (correct, half-open) and `<=` (wrong, reports
 * neighbours as blockers) look equally reasonable in review. Its predecessor `getAt` matched on the
 * anchor alone and silently reported the other three squares of a 2x2 gadget as free.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherCellDaoTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.launcherCellDao()

    private suspend fun place(
        row: Int,
        col: Int,
        spanW: Int = 1,
        spanH: Int = 1,
        orientation: String = PORTRAIT,
        screenIndex: Int = 0,
    ): Long = dao.upsert(
        LauncherCellEntity(
            orientation = orientation,
            rowIndex = row,
            colIndex = col,
            spanW = spanW,
            spanH = spanH,
            kind = "SHORTCUT",
            target = "app:com.example",
            labelOverride = null,
            addedAt = 0L,
            screenIndex = screenIndex,
        )
    )

    private suspend fun blockerAt(
        row: Int,
        col: Int,
        spanW: Int = 1,
        spanH: Int = 1,
        orientation: String = PORTRAIT,
        excludeId: Long = NO_EXCLUSION,
        screenIndex: Int = 0,
    ) = dao.findOverlapping(
        orientation = orientation,
        screenIndex = screenIndex,
        rowIndex = row,
        colIndex = col,
        spanW = spanW,
        spanH = spanH,
        excludeId = excludeId,
    )

    @Test
    fun `a cell on another screen is not a blocker`() = runTest {
        place(row = 1, col = 1, screenIndex = 1)
        assertNull(blockerAt(row = 1, col = 1))
    }

    @Test
    fun `identical square is a blocker`() = runTest {
        val id = place(row = 1, col = 1)
        assertEquals(id, blockerAt(row = 1, col = 1)?.id)
    }

    @Test
    fun `free square has no blocker`() = runTest {
        place(row = 1, col = 1)
        assertNull(blockerAt(row = 5, col = 5))
    }

    @Test
    fun `origin is not a special case`() = runTest {
        val id = place(row = 0, col = 0)
        assertEquals(id, blockerAt(row = 0, col = 0)?.id)
    }

    @Test
    fun `sharing a row but not a column is not an overlap`() = runTest {
        place(row = 2, col = 0)
        assertNull(blockerAt(row = 2, col = 4))
    }

    @Test
    fun `sharing a column but not a row is not an overlap`() = runTest {
        place(row = 0, col = 2)
        assertNull(blockerAt(row = 4, col = 2))
    }

    @Test
    fun `touching neighbour is not an overlap`() = runTest {
        // The 2x1 at columns 0-1 ends exactly where column 2 begins: adjacency must stay free, or every
        // cell would fence off the square beside it.
        place(row = 0, col = 0, spanW = 2)
        assertNull(blockerAt(row = 0, col = 2))
    }

    @Test
    fun `every square under a 2x2 gadget is blocked`() = runTest {
        val gadget = place(row = 0, col = 0, spanW = 2, spanH = 2)
        // (0,0) is the anchor; the other three are what the old anchor-only query reported as free.
        listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1).forEach { (row, col) ->
            assertEquals("square $row,$col", gadget, blockerAt(row = row, col = col)?.id)
        }
    }

    @Test
    fun `a large candidate is blocked by a small cell inside it`() = runTest {
        val shortcut = place(row = 1, col = 1)
        assertEquals(shortcut, blockerAt(row = 0, col = 0, spanW = 2, spanH = 2)?.id)
    }

    @Test
    fun `partial corner touch is an overlap`() = runTest {
        val gadget = place(row = 0, col = 0, spanW = 2, spanH = 2)
        assertEquals(gadget, blockerAt(row = 1, col = 1, spanW = 2, spanH = 2)?.id)
    }

    @Test
    fun `a cell never blocks itself`() = runTest {
        val id = place(row = 1, col = 1, spanW = 2, spanH = 2)
        assertNull(blockerAt(row = 1, col = 1, spanW = 2, spanH = 2, excludeId = id))
    }

    @Test
    fun `excluding one cell still sees the others`() = runTest {
        val moving = place(row = 0, col = 0)
        val other = place(row = 0, col = 1)
        assertEquals(other, blockerAt(row = 0, col = 1, excludeId = moving)?.id)
    }

    @Test
    fun `the other orientation is a different desktop`() = runTest {
        place(row = 0, col = 0, orientation = LANDSCAPE)
        assertNull(blockerAt(row = 0, col = 0, orientation = PORTRAIT))
    }

    private companion object {
        const val PORTRAIT = "PORTRAIT"
        const val LANDSCAPE = "LANDSCAPE"

        /** No real row carries this id, so nothing is excluded. */
        const val NO_EXCLUSION = -1L
    }
}
