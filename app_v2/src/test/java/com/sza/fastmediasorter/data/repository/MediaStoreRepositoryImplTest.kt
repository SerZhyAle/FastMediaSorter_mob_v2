package com.sza.fastmediasorter.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.MediaStore
import com.sza.fastmediasorter.domain.model.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3960: a non-recursive folder listing asks MediaStore for direct children only, instead of pulling
 * every descendant row of the folder and dropping the nested ones in memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreRepositoryImplTest {

    private val selection = slot<String>()
    private val selectionArgs = slot<Array<String>>()

    private fun repositoryReturning(vararg rows: Map<String, Any?>): MediaStoreRepositoryImpl {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(any(), any(), capture(selection), capture(selectionArgs), any())
        } answers {
            val columns = secondArg<Array<String>>()
            MatrixCursor(columns).apply { rows.forEach { row -> addRow(columns.map { row[it] }) } }
        }
        val context = mockk<Context> { every { contentResolver } returns resolver }
        return MediaStoreRepositoryImpl(context)
    }

    private fun imageRow(id: Long, path: String): Map<String, Any?> = mapOf(
        MediaStore.Files.FileColumns._ID to id,
        MediaStore.Files.FileColumns.DATA to path,
        MediaStore.Files.FileColumns.DISPLAY_NAME to path.substringAfterLast('/'),
        MediaStore.Files.FileColumns.SIZE to 10L,
        MediaStore.Files.FileColumns.DATE_MODIFIED to 1_700_000_000L,
        MediaStore.Files.FileColumns.MIME_TYPE to "image/jpeg",
        MediaStore.Files.FileColumns.MEDIA_TYPE to MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
    )

    @Test
    fun `a non-recursive listing excludes nested rows in the query itself`() = runTest {
        val repository = repositoryReturning(imageRow(1L, "/sdcard/DCIM/a.jpg"))

        val files = repository.getFilesInFolder(
            "/sdcard/DCIM",
            setOf(MediaType.IMAGE),
            recursive = false,
            showHiddenFiles = false,
        )

        assertEquals(listOf("/sdcard/DCIM/a.jpg"), files.map { it.path })
        assertTrue(selection.captured.contains("NOT LIKE"))
        assertArrayEquals(arrayOf("/sdcard/DCIM/%", "/sdcard/DCIM/%/%"), selectionArgs.captured)
    }

    @Test
    fun `a standard-folder count takes visible direct children only`() {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(any(), any(), capture(selection), capture(selectionArgs), any())
        } answers {
            val columns = secondArg<Array<String>>()
            MatrixCursor(columns).apply {
                listOf(
                    imageRow(1L, "/sdcard/DCIM/a.jpg"),
                    imageRow(2L, "/sdcard/DCIM/.hidden.jpg"),
                    imageRow(3L, "/sdcard/DCIM/Camera/b.jpg"),
                    imageRow(4L, "/sdcard/DCIM/c.txt") + (MediaStore.Files.FileColumns.MEDIA_TYPE to 0),
                ).forEach { row -> addRow(columns.map { row[it] }) }
            }
        }

        val (count, types) = countMediaStoreDirectChildren(
            resolver = resolver,
            folderPath = "/sdcard/DCIM",
            allowedTypes = setOf(MediaType.IMAGE),
            isTrashPath = { false },
            resolveType = { _, _, mediaType -> if (mediaType == 1) MediaType.IMAGE else MediaType.TEXT },
        )

        assertEquals(1, count)
        assertEquals(setOf(MediaType.IMAGE), types)
        assertArrayEquals(arrayOf("/sdcard/DCIM/%", "/sdcard/DCIM/%/%"), selectionArgs.captured)
    }

    @Test
    fun `a recursive listing keeps every descendant`() = runTest {
        val repository = repositoryReturning(
            imageRow(1L, "/sdcard/DCIM/a.jpg"),
            imageRow(2L, "/sdcard/DCIM/Camera/b.jpg"),
        )

        val files = repository.getFilesInFolder(
            "/sdcard/DCIM/",
            setOf(MediaType.IMAGE),
            recursive = true,
            showHiddenFiles = false,
        )

        assertEquals(2, files.size)
        assertFalse(selection.captured.contains("NOT LIKE"))
        assertArrayEquals(arrayOf("/sdcard/DCIM/%"), selectionArgs.captured)
    }
}
