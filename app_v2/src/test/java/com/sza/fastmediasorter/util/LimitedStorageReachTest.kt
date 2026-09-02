package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2369: pins the whole matrix - two manifests against the four ways a local resource is addressed -
 * without a device, so the sentence the screen prints cannot drift from the sentence the scanner obeys.
 */
class LimitedStorageReachTest {

    private val documents = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.PDF, MediaType.TEXT)
    private val mediaOnly = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF)

    @Test
    fun `raw path on a build without all-files access is limited when it promises documents`() {
        assertTrue(
            LimitedStorageReach.isReachLimited(
                type = ResourceType.LOCAL,
                path = "/storage/emulated/0/Download",
                promisedTypes = documents,
                allFilesAccessMissing = true
            )
        )
    }

    @Test
    fun `raw path promising only media loses nothing to the narrowing`() {
        assertFalse(
            LimitedStorageReach.isReachLimited(
                type = ResourceType.LOCAL,
                path = "/storage/emulated/0/Download",
                promisedTypes = mediaOnly,
                allFilesAccessMissing = true
            )
        )
    }

    @Test
    fun `a build that declares all-files access reads the same folder whole`() {
        assertFalse(
            LimitedStorageReach.isReachLimited(
                type = ResourceType.LOCAL,
                path = "/storage/emulated/0/Download",
                promisedTypes = documents,
                allFilesAccessMissing = false
            )
        )
    }

    @Test
    fun `a tree URI is served by the SAF branch and returns documents today`() {
        assertFalse(
            LimitedStorageReach.isReachLimited(
                type = ResourceType.LOCAL,
                path = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
                promisedTypes = documents,
                allFilesAccessMissing = true
            )
        )
    }

    @Test
    fun `the all-documents aggregate is limited but the media aggregates are not`() {
        assertTrue(
            LimitedStorageReach.isReachLimited(
                type = ResourceType.LOCAL,
                path = "virtual://all_docs",
                promisedTypes = documents,
                allFilesAccessMissing = true
            )
        )
        assertFalse(
            LimitedStorageReach.isReachLimited(
                type = ResourceType.LOCAL,
                path = "virtual://all_images",
                promisedTypes = documents,
                allFilesAccessMissing = true
            )
        )
    }

    @Test
    fun `a network resource never reads through MediaProvider`() {
        assertFalse(
            LimitedStorageReach.isReachLimited(
                type = ResourceType.SMB,
                path = "smb://host/share/docs",
                promisedTypes = documents,
                allFilesAccessMissing = true
            )
        )
    }

    @Test
    fun `narrowing keeps exactly the four types the granular permissions cover`() {
        assertEquals(
            setOf(MediaType.IMAGE, MediaType.VIDEO),
            LimitedStorageReach.narrowToReachable(documents)
        )
        assertTrue(LimitedStorageReach.narrowToReachable(setOf(MediaType.PDF, MediaType.EPUB)).isEmpty())
    }
}
