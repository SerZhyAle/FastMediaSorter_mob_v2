package com.sza.fastmediasorter.core.cache

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3470: the stored lists used to be resized in place, so LruCache subtracted the new length on
 * replace/evict while it had charged the old one, and evictAll() threw IllegalStateException.
 * Robolectric supplies the real android.util.LruCache, whose trimToSize() carries that check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class MediaFilesCacheManagerTest {

    private val resourceId = 3470L

    @After
    fun clear() {
        MediaFilesCacheManager.clearAllCaches()
    }

    @Test
    fun `clearAllCaches after adds to an absent resource does not throw`() {
        MediaFilesCacheManager.addFile(resourceId, file("a"))
        MediaFilesCacheManager.addFile(resourceId, file("b"))

        MediaFilesCacheManager.clearAllCaches()

        assertFalse(MediaFilesCacheManager.isCached(resourceId))
    }

    @Test
    fun `clearAllCaches after add and remove on a cached resource does not throw`() {
        MediaFilesCacheManager.setCachedList(resourceId, listOf(file("a"), file("b"), file("c")))
        MediaFilesCacheManager.addFile(resourceId, file("d"))
        assertTrue(MediaFilesCacheManager.removeFile(resourceId, "/storage/a"))
        assertTrue(MediaFilesCacheManager.removeFile(resourceId, "/storage/b"))

        MediaFilesCacheManager.clearAllCaches()

        assertFalse(MediaFilesCacheManager.isCached(resourceId))
    }

    @Test
    fun `mutators leave a returned snapshot untouched`() {
        MediaFilesCacheManager.setCachedList(resourceId, listOf(file("a"), file("b")))
        val snapshot = MediaFilesCacheManager.getCachedList(resourceId)

        MediaFilesCacheManager.addFile(resourceId, file("c"))
        MediaFilesCacheManager.updateFile(resourceId, "/storage/a", file("z"))

        assertEquals(listOf("/storage/a", "/storage/b"), snapshot?.map { it.path })
        assertEquals(
            listOf("/storage/z", "/storage/b", "/storage/c"),
            MediaFilesCacheManager.getCachedList(resourceId)?.map { it.path }
        )
    }

    private fun file(name: String) = MediaFile(
        name = name,
        path = "/storage/$name",
        type = MediaType.IMAGE,
        size = 1L,
        createdDate = 0L
    )
}
