package com.sza.fastmediasorter.ui.browse.helpers

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseItemOperationPolicyTest {

    private val folder = MediaFile(
        name = "Holiday",
        path = "/storage/emulated/0/DCIM/Holiday",
        type = MediaType.IMAGE,
        size = 0L,
        createdDate = 0L,
        isDirectory = true,
    )

    private val file = MediaFile(
        name = "clip.mp4",
        path = "/storage/emulated/0/DCIM/clip.mp4",
        type = MediaType.VIDEO,
        size = 1024L,
        createdDate = 0L,
    )

    @Test
    fun `folder is selectable`() {
        assertTrue(BrowseItemOperationPolicy.isSelectable(folder))
    }

    @Test
    fun `file is selectable`() {
        assertTrue(BrowseItemOperationPolicy.isSelectable(file))
    }

    @Test
    fun `folder supports transfer and naming operations`() {
        val supported = listOf(
            BrowseItemOperation.SELECT,
            BrowseItemOperation.COPY,
            BrowseItemOperation.MOVE,
            BrowseItemOperation.RENAME,
            BrowseItemOperation.DELETE,
            BrowseItemOperation.REORDER,
        )
        supported.forEach { operation ->
            assertTrue(
                "folder should support $operation",
                BrowseItemOperationPolicy.supports(operation, folder),
            )
        }
    }

    @Test
    fun `folder refuses single-file operations`() {
        val refused = listOf(
            BrowseItemOperation.FAVORITE,
            BrowseItemOperation.OPEN_IN_PLAYER,
            BrowseItemOperation.SEND_TO,
            BrowseItemOperation.INFO,
            BrowseItemOperation.EXTRACT_ARCHIVE,
        )
        refused.forEach { operation ->
            assertFalse(
                "folder should refuse $operation",
                BrowseItemOperationPolicy.supports(operation, folder),
            )
        }
    }

    @Test
    fun `file supports every operation`() {
        BrowseItemOperation.entries.forEach { operation ->
            assertTrue(
                "file should support $operation",
                BrowseItemOperationPolicy.supports(operation, file),
            )
        }
    }
}
