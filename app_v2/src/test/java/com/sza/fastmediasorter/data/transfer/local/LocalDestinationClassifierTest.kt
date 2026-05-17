package com.sza.fastmediasorter.data.transfer.local

import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [LocalDestinationClassifier].
 *
 * Pure path arithmetic + `MimeTypeMap` — Robolectric is sufficient to back
 * `Environment.getExternalStorageDirectory()` and the MIME map.
 *
 * See spec S0231 §6.1 (public collection boundary) and §6.3 (MIME).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDestinationClassifierTest {

    private val classifier = LocalDestinationClassifier()
    private val externalRoot = Environment.getExternalStorageDirectory().absolutePath

    @Test
    fun `music subdirectory maps to AUDIO public collection`() {
        val path = "$externalRoot/Music/Artist/song.mp3"

        val result = classifier.classify(path)

        assertTrue("expected PublicCollection, got $result", result is LocalDestinationCategory.PublicCollection)
        val pub = result as LocalDestinationCategory.PublicCollection
        assertEquals(LocalDestinationCategory.PublicCollection.Kind.AUDIO, pub.collection)
        assertEquals("Music/Artist/", pub.relativePath)
        assertEquals("song.mp3", pub.displayName)
        assertEquals("audio/mpeg", pub.mimeType)
    }

    @Test
    fun `movies root maps to VIDEO public collection`() {
        val path = "$externalRoot/Movies/film.mp4"

        val result = classifier.classify(path)

        assertTrue(result is LocalDestinationCategory.PublicCollection)
        val pub = result as LocalDestinationCategory.PublicCollection
        assertEquals(LocalDestinationCategory.PublicCollection.Kind.VIDEO, pub.collection)
        assertEquals("Movies/", pub.relativePath)
        assertEquals("video/mp4", pub.mimeType)
    }

    @Test
    fun `download root maps to DOWNLOADS public collection`() {
        val path = "$externalRoot/Download/doc.pdf"

        val result = classifier.classify(path)

        assertTrue(result is LocalDestinationCategory.PublicCollection)
        val pub = result as LocalDestinationCategory.PublicCollection
        assertEquals(LocalDestinationCategory.PublicCollection.Kind.DOWNLOADS, pub.collection)
        assertEquals("Download/", pub.relativePath)
        assertEquals("application/pdf", pub.mimeType)
    }

    @Test
    fun `non-canonical external path maps to NonPublic`() {
        val path = "$externalRoot/SomeApp/data.bin"

        val result = classifier.classify(path)

        assertTrue("expected NonPublic, got $result", result is LocalDestinationCategory.NonPublic)
        val np = result as LocalDestinationCategory.NonPublic
        assertEquals(path, np.absolutePath)
        assertEquals("data.bin", np.displayName)
        assertEquals("application/octet-stream", np.mimeType)
    }

    @Test
    fun `path outside external storage maps to NonPublic`() {
        val path = "/data/data/com.sza.fastmediasorter/files/cache.tmp"

        val result = classifier.classify(path)

        assertTrue(result is LocalDestinationCategory.NonPublic)
    }

    @Test
    fun `extension-less file resolves to octet-stream mime`() {
        val path = "$externalRoot/Music/playlist"

        val result = classifier.classify(path)

        assertTrue(result is LocalDestinationCategory.PublicCollection)
        assertEquals("application/octet-stream", (result as LocalDestinationCategory.PublicCollection).mimeType)
    }
}
