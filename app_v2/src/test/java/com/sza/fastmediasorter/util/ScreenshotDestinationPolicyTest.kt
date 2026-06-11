package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotDestinationPolicyTest {

    private val policy = ScreenshotDestinationPolicy()

    @Test
    fun `selected resource wins when id is present`() {
        val selected = resource(id = 7L, name = "Chosen")

        val result = policy.resolve(
            selectedResourceId = "7",
            resources = listOf(selected)
        )

        assertTrue(result is ScreenshotDestinationPolicy.Target.SelectedResource)
        assertEquals(selected, (result as ScreenshotDestinationPolicy.Target.SelectedResource).resource)
    }

    @Test
    fun `screenshots folder is chosen when no resource is selected`() {
        val result = policy.resolve(
            selectedResourceId = null,
            resources = emptyList()
        )

        assertTrue(result is ScreenshotDestinationPolicy.Target.PublicCollection)
        result as ScreenshotDestinationPolicy.Target.PublicCollection
        assertEquals(LocalDestinationCategory.PublicCollection.Kind.IMAGES, result.collection)
        assertEquals(
            ScreenshotDestinationPolicy.PICTURES_SCREENSHOTS_RELATIVE_PATH,
            result.relativePath
        )
    }

    @Test
    fun `downloads is chosen when screenshots folders are unavailable`() {
        val result = policy.resolve(
            selectedResourceId = null,
            resources = emptyList(),
            availableScreenshotRelativePaths = emptySet()
        )

        assertTrue(result is ScreenshotDestinationPolicy.Target.PublicCollection)
        result as ScreenshotDestinationPolicy.Target.PublicCollection
        assertEquals(LocalDestinationCategory.PublicCollection.Kind.DOWNLOADS, result.collection)
        assertEquals(ScreenshotDestinationPolicy.DOWNLOADS_RELATIVE_PATH, result.relativePath)
    }

    private fun resource(id: Long, name: String): MediaResource = MediaResource(
        id = id,
        name = name,
        path = "/tmp/$name",
        type = ResourceType.LOCAL,
        supportedMediaTypes = setOf(MediaType.IMAGE),
        isWritable = true
    )
}
