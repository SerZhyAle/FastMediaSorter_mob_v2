package com.sza.fastmediasorter.wear.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S3359: pins the single answer two sides read - the policy withholds "to watch" when it is null and
 * the publisher writes into the folder it names.
 *
 * A wrong answer here is never a local defect: it is either an operation offered on a file that can
 * never be placed, or a copy written where the watch's own category lists do not look.
 */
class WearWatchFileTargetTest {

    @Test
    fun `an audio type lands in the music folder`() {
        val collection = WearWatchFileTarget.collectionOf("audio/mpeg")

        assertEquals(WearWatchFileCollection.AUDIO, collection)
        assertEquals("Music/FastMediaSorter/", WearWatchFileTarget.relativePathOf(WearWatchFileCollection.AUDIO))
    }

    @Test
    fun `a video type lands in the movies folder`() {
        val collection = WearWatchFileTarget.collectionOf("video/mp4")

        assertEquals(WearWatchFileCollection.VIDEO, collection)
        assertEquals("Movies/FastMediaSorter/", WearWatchFileTarget.relativePathOf(WearWatchFileCollection.VIDEO))
    }

    @Test
    fun `an image type lands in the pictures folder`() {
        val collection = WearWatchFileTarget.collectionOf("image/jpeg")

        assertEquals(WearWatchFileCollection.IMAGE, collection)
        assertEquals("Pictures/FastMediaSorter/", WearWatchFileTarget.relativePathOf(WearWatchFileCollection.IMAGE))
    }

    @Test
    fun `an upper-case type is read as the same collection`() {
        assertEquals(WearWatchFileCollection.IMAGE, WearWatchFileTarget.collectionOf("IMAGE/HEIC"))
    }

    @Test
    fun `a document type has no collection`() {
        assertNull(WearWatchFileTarget.collectionOf("application/pdf"))
    }

    @Test
    fun `a blank type has no collection`() {
        assertNull(WearWatchFileTarget.collectionOf("   "))
    }

    @Test
    fun `an undeclared type has no collection`() {
        assertNull(WearWatchFileTarget.collectionOf(null))
    }
}
