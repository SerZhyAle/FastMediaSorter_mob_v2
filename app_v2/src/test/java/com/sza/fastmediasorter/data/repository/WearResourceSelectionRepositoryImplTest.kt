package com.sza.fastmediasorter.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [WearResourceSelectionRepositoryImpl]: the set of resource ids marked for transfer
 * to the watch. The first assertion is the load-bearing one - an absent key must read as "nothing
 * selected", because reading it as "everything" would push every registered resource to the watch.
 * Robolectric supplies the real SharedPreferences implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearResourceSelectionRepositoryImplTest {

    private val context = RuntimeEnvironment.getApplication()
    private val repo = WearResourceSelectionRepositoryImpl(context)

    @Test
    fun `getSelectedIds is empty before any write`() {
        assertTrue(repo.getSelectedIds().isEmpty())
    }

    @Test
    fun `setSelectedIds round-trips exactly the written set`() {
        repo.setSelectedIds(setOf(1L, 2L))

        assertEquals(setOf(1L, 2L), repo.getSelectedIds())
    }

    @Test
    fun `selectAll stores every id it is given`() {
        repo.selectAll(setOf(1L, 2L, 3L))

        assertEquals(setOf(1L, 2L, 3L), repo.getSelectedIds())
    }

    @Test
    fun `deselecting one id survives a new repository instance`() {
        repo.setSelectedIds(setOf(1L, 2L, 3L))

        repo.setSelectedIds(setOf(1L, 3L))

        assertEquals(setOf(1L, 3L), WearResourceSelectionRepositoryImpl(context).getSelectedIds())
    }
}
