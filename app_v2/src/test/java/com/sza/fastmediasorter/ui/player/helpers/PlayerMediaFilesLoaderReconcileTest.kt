package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMediaFilesLoaderReconcileTest {

    @Test
    fun `reconcileFavoriteFlags keeps the failing element untouched and continues with siblings`() {
        val first = mediaFile("sftp://host/library/first.mp3")
        val broken = mediaFile("sftp://host/library/broken.mp3")
        val third = mediaFile("sftp://host/library/third.mp3")
        val failedPaths = mutableListOf<String>()

        val result = reconcileFavoriteFlags(
            files = listOf(first, broken, third),
            favoriteMap = mapOf(
                first.path to true,
                broken.path to true,
                third.path to false
            ),
            copyFavorite = { file, isFavorite ->
                if (file.path == broken.path) {
                    throw IllegalStateException("corrupted MediaFile")
                }
                file.copy(isFavorite = isFavorite)
            },
            onFailure = { file, _ ->
                failedPaths += file.path
            }
        )

        assertEquals(3, result.size)
        assertTrue(result[0].isFavorite)
        assertSame(broken, result[1])
        assertFalse(result[1].isFavorite)
        assertFalse(result[2].isFavorite)
        assertEquals(listOf(broken.path), failedPaths)
    }

    private fun mediaFile(path: String): MediaFile = MediaFile(
        name = path.substringAfterLast('/'),
        path = path,
        type = MediaType.AUDIO,
        size = 1024L,
        createdDate = 1L
    )
}