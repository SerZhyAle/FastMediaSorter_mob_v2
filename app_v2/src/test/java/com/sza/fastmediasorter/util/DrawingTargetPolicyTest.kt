package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingTargetPolicyTest {
    @Test
    fun `canCreateDrawing allows virtual all images`() {
        val resource = imageResource(path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES)

        assertTrue(DrawingTargetPolicy.canCreateDrawing(resource))
    }

    @Test
    fun `canCreateDrawing allows virtual camera photos`() {
        val resource = imageResource(path = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS)

        assertTrue(DrawingTargetPolicy.canCreateDrawing(resource))
    }

    @Test
    fun `canCreateDrawing rejects other virtual resources`() {
        val resource = imageResource(path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO)

        assertFalse(DrawingTargetPolicy.canCreateDrawing(resource))
    }

    @Test
    fun `canCreateDrawing rejects read-only all images`() {
        val resource = imageResource(
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            isReadOnly = true
        )

        assertFalse(DrawingTargetPolicy.canCreateDrawing(resource))
    }

    @Test
    fun `canCreateDrawing rejects non-image resources`() {
        val resource = MediaResource(
            id = 1L,
            name = "Videos",
            path = "/storage/emulated/0/Movies",
            type = ResourceType.LOCAL,
            supportedMediaTypes = setOf(MediaType.VIDEO)
        )

        assertFalse(DrawingTargetPolicy.canCreateDrawing(resource))
    }

    @Test
    fun `canCreateDrawing allows normal local image folder`() {
        val resource = imageResource(path = "/storage/emulated/0/Pictures")

        assertTrue(DrawingTargetPolicy.canCreateDrawing(resource))
    }

    private fun imageResource(
        path: String,
        isReadOnly: Boolean = false
    ): MediaResource = MediaResource(
        id = 1L,
        name = "Images",
        path = path,
        type = ResourceType.LOCAL,
        supportedMediaTypes = setOf(MediaType.IMAGE),
        isReadOnly = isReadOnly
    )
}
