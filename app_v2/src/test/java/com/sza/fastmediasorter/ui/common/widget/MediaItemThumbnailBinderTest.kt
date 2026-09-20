package com.sza.fastmediasorter.ui.common.widget

import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3246: pins the claim the binder exists to make - the Glide disk-cache strategy and override size
 * are decided by [ThumbnailRole] alone, never by a call site. A regression here is invisible in a
 * screenshot, which is why it is asserted rather than eyeballed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class MediaItemThumbnailBinderTest {

    private val binder = MediaItemThumbnailBinder()

    @Test
    fun `list row and grid cell share one cached-thumbnail configuration`() {
        val listRow = binder.requestOptionsFor(ThumbnailRole.LIST_ROW)
        val gridCell = binder.requestOptionsFor(ThumbnailRole.GRID_CELL)

        assertEquals(DiskCacheStrategy.ALL, listRow.diskCacheStrategy)
        assertEquals(DiskCacheStrategy.ALL, gridCell.diskCacheStrategy)
        assertEquals(MediaItemThumbnailBinder.CACHED_THUMBNAIL_SIZE, listRow.overrideWidth)
        assertEquals(MediaItemThumbnailBinder.CACHED_THUMBNAIL_SIZE, listRow.overrideHeight)
        assertEquals(listRow.overrideWidth, gridCell.overrideWidth)
        assertEquals(listRow.overrideHeight, gridCell.overrideHeight)
    }

    @Test
    fun `preview keeps the full-size resource strategy and no override`() {
        val preview = binder.requestOptionsFor(ThumbnailRole.PREVIEW)

        assertEquals(DiskCacheStrategy.RESOURCE, preview.diskCacheStrategy)
        assertFalse("preview must not be downsized to the cached thumbnail size", preview.isValidOverride)
    }

    @Test
    fun `cached thumbnail size is a usable positive dimension`() {
        assertTrue(MediaItemThumbnailBinder.CACHED_THUMBNAIL_SIZE > 0)
    }

    @Test
    fun `every role yields its own options instance`() {
        ThumbnailRole.entries.forEach { role ->
            assertNotNull("no options for $role", binder.requestOptionsFor(role))
        }
    }
}
