package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.ui.main.ResourceTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0488: the predefined All-files resource must stay first under any sort mode, yet disappear when the
 * active tab or a filter excludes it. The All-files fixture is deliberately last under every natural
 * ordering (name Z, oldest date, zero count, highest displayOrder) so a passing test proves the pin,
 * not a coincidental sort.
 */
class ResourceFilterManagerTest {

    private val manager = ResourceFilterManager()

    private val allFiles = createMediaResource(
        id = 10L,
        name = "Zzz-AllFiles",
        path = "/storage/emulated/0",
        type = ResourceType.LOCAL,
        profile = ResourceProfile.ALL_FILES,
        allFiles = true,
        displayOrder = 99,
        createdDate = 50L,
        fileCount = 0,
    )
    private val alpha = createMediaResource(id = 1L, name = "Alpha", displayOrder = 0, createdDate = 100L, fileCount = 5)
    private val beta = createMediaResource(id = 2L, name = "Beta", displayOrder = 1, createdDate = 200L, fileCount = 10)
    private val gamma = createMediaResource(id = 3L, name = "Gamma", displayOrder = 2, createdDate = 300L, fileCount = 1)
    private val smb = createMediaResource(
        id = 4L,
        name = "Server",
        type = ResourceType.SMB,
        displayOrder = 3,
        createdDate = 400L,
        fileCount = 20,
    )

    private val all = listOf(alpha, beta, allFiles, gamma, smb)

    @Test
    fun `all files pinned first under every sort mode`() {
        val sortModes = listOf(
            SortMode.NAME_ASC,
            SortMode.NAME_DESC,
            SortMode.DATE_ASC,
            SortMode.DATE_DESC,
            SortMode.SIZE_ASC,
            SortMode.SIZE_DESC,
            SortMode.MANUAL,
        )
        for (mode in sortModes) {
            val result = manager.applyFiltersAndSorting(all, ResourceTab.ALL, sortMode = mode)
            assertEquals("All-files must be first under $mode", allFiles.id, result.first().id)
        }
    }

    @Test
    fun `all files hidden on non-local tab`() {
        val result = manager.applyFiltersAndSorting(all, ResourceTab.SMB, sortMode = SortMode.NAME_ASC)
        assertFalse(result.any { it.id == allFiles.id })
        assertTrue(result.any { it.id == smb.id })
    }

    @Test
    fun `all files hidden by excluding type filter`() {
        val result = manager.applyFiltersAndSorting(
            all,
            ResourceTab.ALL,
            filterByType = setOf(ResourceType.SMB),
            sortMode = SortMode.NAME_ASC,
        )
        assertFalse(result.any { it.id == allFiles.id })
        assertTrue(result.any { it.id == smb.id })
    }

    @Test
    fun `all files hidden by non-matching name filter`() {
        val result = manager.applyFiltersAndSorting(
            all,
            ResourceTab.ALL,
            filterByName = "Server",
            sortMode = SortMode.NAME_ASC,
        )
        assertFalse(result.any { it.id == allFiles.id })
        assertTrue(result.any { it.id == smb.id })
    }

    @Test
    fun `other resources keep relative order after hoist`() {
        val result = manager.applyFiltersAndSorting(all, ResourceTab.ALL, sortMode = SortMode.NAME_ASC)
        assertEquals(allFiles.id, result.first().id)
        // Remaining order equals the others sorted name-ascending, unaffected by the hoist.
        assertEquals(listOf(alpha.id, beta.id, gamma.id, smb.id), result.drop(1).map { it.id })
    }

    @Test
    fun `pinAllFilesFirst hoists predefined resource for db-sorted lists`() {
        // The ViewModel's loadResources() path gets an already filtered+sorted list straight from the DB
        // (getResourcesUseCase.getFiltered) and must still pin via this public entry point.
        val dbSorted = listOf(alpha, beta, gamma, smb, allFiles) // All-files placed last by the DB sort
        val result = manager.pinAllFilesFirst(dbSorted)
        assertEquals(allFiles.id, result.first().id)
        assertEquals(listOf(alpha.id, beta.id, gamma.id, smb.id), result.drop(1).map { it.id })
    }

    @Test
    fun `pinAllFilesFirst leaves list unchanged when no predefined resource`() {
        val noAllFiles = listOf(alpha, beta, gamma)
        assertEquals(listOf(alpha.id, beta.id, gamma.id), manager.pinAllFilesFirst(noAllFiles).map { it.id })
    }
}
