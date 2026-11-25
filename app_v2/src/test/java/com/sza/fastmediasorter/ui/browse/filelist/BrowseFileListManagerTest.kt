package com.sza.fastmediasorter.ui.browse.filelist

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BrowseFileListManagerTest {

    private val fileListManager = BrowseFileListManager(resourceId = 1L)

    @Test
    fun `random sort uses provided seed deterministically`() {
        val files = buildFiles()

        val firstShuffle = fileListManager.sortFiles(files, SortMode.RANDOM, forceSort = true, randomSeed = 101L)
        val repeatedShuffle = fileListManager.sortFiles(files, SortMode.RANDOM, forceSort = true, randomSeed = 101L)
        val differentSeedShuffle = fileListManager.sortFiles(files, SortMode.RANDOM, forceSort = true, randomSeed = 202L)

        assertEquals(firstShuffle.map { it.path }, repeatedShuffle.map { it.path })
        assertNotEquals(firstShuffle.map { it.path }, differentSeedShuffle.map { it.path })
    }

    @Test
    fun `sortFiles_emptyList_returnsEmpty`() {
        val result = fileListManager.sortFiles(emptyList(), SortMode.NAME_ASC, forceSort = true, randomSeed = 0L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sortFiles_emptyList_randomMode_returnsEmpty`() {
        val result = fileListManager.sortFiles(emptyList(), SortMode.RANDOM, forceSort = true, randomSeed = 0L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `folders precede files and collate in the interface locale`() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale("ru"))
        try {
            val manager = BrowseFileListManager(resourceId = 1L)
            val items = listOf(
                mediaFile("alpha.mp3", 1L),
                folder("яблоко"),
                folder("ёлка"),
                folder("Ель"),
                folder("жираф"),
            )
            val sorted = manager.sortFiles(items, SortMode.NAME_DESC, forceSort = true).map { it.name }
            assertEquals("alpha.mp3", sorted.last())
            val folders = sorted.dropLast(1)
            assertEquals("яблоко", folders.last())
            assertTrue("ё stays beside е: $folders", setOf("ёлка", "Ель") == folders.take(2).toSet())
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `folder case difference does not order`() {
        val manager = BrowseFileListManager(resourceId = 1L)
        val folders = listOf(folder("beta"), folder("Alpha"), folder("alpha2"))
        val sorted = manager.sortFiles(folders, SortMode.NAME_ASC, forceSort = true)
        assertEquals(listOf("Alpha", "alpha2", "beta"), sorted.map { it.name })
    }

    private fun folder(name: String): MediaFile = MediaFile(
        name = name,
        path = "/music/$name",
        type = MediaType.AUDIO,
        size = 0L,
        createdDate = 0L,
        isDirectory = true,
    )

    private fun buildFiles(): List<MediaFile> {
        return listOf(
            mediaFile("alpha.mp3", 1L),
            mediaFile("bravo.mp3", 2L),
            mediaFile("charlie.mp3", 3L),
            mediaFile("delta.mp3", 4L),
            mediaFile("echo.mp3", 5L)
        )
    }

    private fun mediaFile(name: String, createdDate: Long): MediaFile {
        return MediaFile(
            name = name,
            path = "/music/$name",
            type = MediaType.AUDIO,
            size = createdDate * 100,
            createdDate = createdDate
        )
    }
}
