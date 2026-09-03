package com.sza.fastmediasorter.wear.data.recorder

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * S2161: decision table tests for [VoiceNotePublisher].
 */
class VoiceNotePublisherTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val contentResolver: ContentResolver = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        // publish() resolves a MIME type through the platform table, which on the plain JVM is an
        // unmocked stub that throws. Answering null leaves the decision to the publisher's own
        // FALLBACK_MIME_TYPES, so the path under test stays the production one.
        mockkStatic(MimeTypeMap::class)
        val mimeTypeMap: MimeTypeMap = mockk()
        every { MimeTypeMap.getSingleton() } returns mimeTypeMap
        every { mimeTypeMap.getMimeTypeFromExtension(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic(MimeTypeMap::class)
        unmockkConstructor(ContentValues::class)
    }

    @Test
    fun `refuses publication below API 29 without calling content resolver`() {
        val publisher = VoiceNotePublisher(
            contentResolver = contentResolver,
            sdkIntProvider = { Build.VERSION_CODES.P }
        )
        val file = temporaryFolder.newFile("audio_test.m4a").apply { writeText("audio content") }

        val result = publisher.publish(file)

        assertNull(result)
        verify(exactly = 0) { contentResolver.insert(any(), any()) }
    }

    @Test
    fun `uses canonical Recordings folder below API 31`() {
        assertEquals("Recordings", VoiceNotePublisher.recordingsDirectoryName(Build.VERSION_CODES.Q))
        assertEquals("Recordings", VoiceNotePublisher.recordingsDirectoryName(Build.VERSION_CODES.R))
        assertEquals("Recordings/", VoiceNotePublisher.recordingsRelativePath(Build.VERSION_CODES.Q))
    }

    @Test
    fun `uses DIRECTORY_RECORDINGS on API 31 and above`() {
        assertEquals(
            VoiceNotePublisher.DIRECTORY_RECORDINGS_CANONICAL,
            VoiceNotePublisher.recordingsDirectoryName(Build.VERSION_CODES.S)
        )
        assertEquals(
            VoiceNotePublisher.DIRECTORY_RECORDINGS_CANONICAL,
            VoiceNotePublisher.recordingsDirectoryName(Build.VERSION_CODES.TIRAMISU)
        )
        assertEquals(
            "${VoiceNotePublisher.DIRECTORY_RECORDINGS_CANONICAL}/",
            VoiceNotePublisher.recordingsRelativePath(Build.VERSION_CODES.S)
        )
    }

    @Test
    fun `failed insert reports null without throwing`() {
        every { contentResolver.insert(any(), any()) } throws SecurityException("Permission denied")

        val publisher = VoiceNotePublisher(
            contentResolver = contentResolver,
            sdkIntProvider = { Build.VERSION_CODES.Q }
        )
        val file = temporaryFolder.newFile("audio_fail.m4a").apply { writeText("audio content") }

        val result = publisher.publish(file)

        assertNull(result)
    }

    @Test
    fun `failed stream copy cleans up pending entry and returns null`() {
        val fakeUri = mockk<Uri>(relaxed = true)
        every { Uri.parse("content://media/external/audio/media/123") } returns fakeUri
        every { contentResolver.insert(any(), any()) } returns fakeUri
        every { contentResolver.openOutputStream(fakeUri) } throws IOException("Disk full")

        val publisher = VoiceNotePublisher(
            contentResolver = contentResolver,
            sdkIntProvider = { Build.VERSION_CODES.Q }
        )
        val file = temporaryFolder.newFile("audio_io_fail.m4a").apply { writeText("audio content") }

        val result = publisher.publish(file)

        assertNull(result)
        verify(exactly = 1) { contentResolver.delete(fakeUri, null, null) }
    }

    @Test
    fun `successful publish writes bytes and commits IS_PENDING`() {
        val fakeUri = mockk<Uri>(relaxed = true)
        val outputStream = ByteArrayOutputStream()
        every { contentResolver.insert(any(), any()) } returns fakeUri
        every { contentResolver.openOutputStream(fakeUri) } returns outputStream
        every { contentResolver.update(fakeUri, any(), any(), any()) } returns 1

        val publisher = VoiceNotePublisher(
            contentResolver = contentResolver,
            sdkIntProvider = { Build.VERSION_CODES.Q }
        )
        val file = temporaryFolder.newFile("audio_success.m4a").apply { writeText("sample audio data") }

        val result = publisher.publish(file)

        assertEquals(fakeUri, result)
        assertEquals("sample audio data", outputStream.toString())
        verify {
            contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                any()
            )
        }
        verify {
            contentResolver.update(
                fakeUri,
                any(),
                null,
                null
            )
        }
    }
}
