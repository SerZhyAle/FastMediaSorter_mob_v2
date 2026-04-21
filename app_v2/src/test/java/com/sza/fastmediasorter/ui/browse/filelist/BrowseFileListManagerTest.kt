package com.sza.fastmediasorter.ui.browse.filelist

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

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