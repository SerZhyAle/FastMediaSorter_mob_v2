package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNoteTargetPolicyTest {
    @Test
    fun `canCreateTextNote allows virtual all documents`() {
        val resource = docsResource(path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS)

        assertTrue(TextNoteTargetPolicy.canCreateTextNote(resource))
    }

    @Test
    fun `canCreateTextNote rejects other virtual resources`() {
        val resource = docsResource(path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES)

        assertFalse(TextNoteTargetPolicy.canCreateTextNote(resource))
    }

    @Test
    fun `canCreateTextNote rejects read-only all documents`() {
        val resource = docsResource(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
            isReadOnly = true
        )

        assertFalse(TextNoteTargetPolicy.canCreateTextNote(resource))
    }

    @Test
    fun `canCreateTextNote rejects non-document resources`() {
        val resource = MediaResource(
            id = 1L,
            name = "Images",
            path = "/storage/emulated/0/DCIM",
            type = ResourceType.LOCAL,
            supportedMediaTypes = setOf(MediaType.IMAGE)
        )

        assertFalse(TextNoteTargetPolicy.canCreateTextNote(resource))
    }

    private fun docsResource(
        path: String,
        isReadOnly: Boolean = false
    ): MediaResource = MediaResource(
        id = 1L,
        name = "Docs",
        path = path,
        type = ResourceType.LOCAL,
        supportedMediaTypes = setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB),
        isReadOnly = isReadOnly
    )
}
