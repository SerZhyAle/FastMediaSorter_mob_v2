package com.sza.fastmediasorter.ui.browse.helpers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BrowseSwipeAction
import com.sza.fastmediasorter.domain.model.BrowseSwipeDirection
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2533: every refusal in the swipe chain. Strategic §11 criteria 3 to 7 and 10 are decided here and
 * checked by nothing else - no compile and no gate sees them.
 */
class BrowseSwipeActionResolverTest {

    private val resolver = BrowseSwipeActionResolver()

    private val plainFile = MediaFile(
        name = "photo.jpg",
        path = "/root/photo.jpg",
        type = MediaType.IMAGE,
        size = 100L,
        createdDate = 0L,
    )

    private val folder = MediaFile(
        name = "album",
        path = "/root/album",
        type = MediaType.IMAGE,
        size = 0L,
        createdDate = 0L,
        isDirectory = true,
    )

    private val writableResource = MediaResource(
        name = "local",
        path = "/root",
        type = ResourceType.LOCAL,
        isWritable = true,
    )

    private val readOnlyResource = writableResource.copy(isReadOnly = true)

    @Suppress("LongParameterList")
    private fun resolve(
        file: MediaFile = plainFile,
        direction: BrowseSwipeDirection = BrowseSwipeDirection.LEFT,
        settings: AppSettings = AppSettings(),
        resource: MediaResource? = writableResource,
        hasDestinations: Boolean = true,
        isHorizontalAxisFree: Boolean = true,
        isSelectionActive: Boolean = false,
    ): BrowseSwipeAction? = resolver.resolve(
        file = file,
        direction = direction,
        settings = settings,
        resource = resource,
        hasDestinations = hasDestinations,
        isHorizontalAxisFree = isHorizontalAxisFree,
        isSelectionActive = isSelectionActive,
    )

    @Test
    fun `defaults resolve to delete on the left and send-to on the right`() {
        assertEquals(BrowseSwipeAction.DELETE, resolve(direction = BrowseSwipeDirection.LEFT))
        assertEquals(BrowseSwipeAction.SEND_TO, resolve(direction = BrowseSwipeDirection.RIGHT))
    }

    @Test
    fun `multi-column layout refuses the swipe`() {
        assertNull(resolve(isHorizontalAxisFree = false))
        assertNull(resolve(direction = BrowseSwipeDirection.RIGHT, isHorizontalAxisFree = false))
    }

    @Test
    fun `a single-column grid refuses the swipe because drag still owns the horizontal axis`() {
        // GRID mode computes a span of 1 on a narrow phone, and the touch callback hands drag all
        // four directions under any GridLayoutManager - so the caller reports the axis as taken.
        assertNull(resolve(isHorizontalAxisFree = false))
        assertNull(resolve(file = folder, isHorizontalAxisFree = false))
        assertNull(resolve(direction = BrowseSwipeDirection.RIGHT, isHorizontalAxisFree = false))
    }

    @Test
    fun `an active selection refuses the swipe`() {
        assertNull(resolve(isSelectionActive = true))
        assertNull(resolve(direction = BrowseSwipeDirection.RIGHT, isSelectionActive = true))
    }

    @Test
    fun `a direction set to NONE refuses the swipe`() {
        val settings = BrowseSwipeDirection.LEFT.withAction(AppSettings(), BrowseSwipeAction.NONE)
        assertNull(resolve(settings = settings))
        // The other direction is untouched by that choice.
        assertEquals(BrowseSwipeAction.SEND_TO, resolve(direction = BrowseSwipeDirection.RIGHT, settings = settings))
    }

    @Test
    fun `send-to is refused on a folder row`() {
        assertNull(resolve(file = folder, direction = BrowseSwipeDirection.RIGHT))
    }

    @Test
    fun `delete still resolves on a folder row`() {
        assertEquals(BrowseSwipeAction.DELETE, resolve(file = folder, direction = BrowseSwipeDirection.LEFT))
    }

    @Test
    fun `a write action is refused when the resource refuses writes`() {
        assertNull(resolve(resource = readOnlyResource))
        assertNull(resolve(resource = null))
    }

    @Test
    fun `a non-write action still resolves on a read-only resource`() {
        assertEquals(
            BrowseSwipeAction.SEND_TO,
            resolve(direction = BrowseSwipeDirection.RIGHT, resource = readOnlyResource),
        )
    }

    @Test
    fun `delete is refused when the allowDelete toggle is off`() {
        assertNull(resolve(settings = AppSettings(allowDelete = false)))
    }

    @Test
    fun `copy is refused when no destinations are configured`() {
        val settings = BrowseSwipeDirection.LEFT.withAction(AppSettings(), BrowseSwipeAction.COPY)
        assertNull(resolve(settings = settings, hasDestinations = false))
        assertEquals(BrowseSwipeAction.COPY, resolve(settings = settings, hasDestinations = true))
    }

    @Test
    fun `copy is refused when the enableCopying toggle is off`() {
        val settings = BrowseSwipeDirection.LEFT
            .withAction(AppSettings(enableCopying = false), BrowseSwipeAction.COPY)
        assertNull(resolve(settings = settings))
    }

    @Test
    fun `extract is refused on a file that is not a zip archive`() {
        val settings = BrowseSwipeDirection.LEFT
            .withAction(AppSettings(), BrowseSwipeAction.EXTRACT_ARCHIVE)
        assertNull(resolve(settings = settings))

        val zip = plainFile.copy(name = "bundle.zip", type = MediaType.BINARY_ARCHIVE)
        assertEquals(BrowseSwipeAction.EXTRACT_ARCHIVE, resolve(file = zip, settings = settings))
    }

    @Test
    fun `open in player is refused for a binary file`() {
        val settings = BrowseSwipeDirection.LEFT
            .withAction(AppSettings(), BrowseSwipeAction.OPEN_IN_PLAYER)
        val binary = plainFile.copy(name = "bundle.zip", type = MediaType.BINARY_ARCHIVE)
        assertNull(resolve(file = binary, settings = settings))
        assertEquals(BrowseSwipeAction.OPEN_IN_PLAYER, resolve(settings = settings))
    }
}
