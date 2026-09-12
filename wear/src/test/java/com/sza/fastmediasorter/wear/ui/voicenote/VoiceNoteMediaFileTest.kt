package com.sza.fastmediasorter.wear.ui.voicenote

import android.net.Uri
import android.webkit.MimeTypeMap
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * S2495: the mapping that lets the module's shared file surfaces take a voice note.
 *
 * The address choice is what these assertions pin. It reversed the tactical plan's instruction, so a
 * later change flipping it back would be a decision and not an accident.
 */
class VoiceNoteMediaFileTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers { fakeUri(PRIVATE_URI) }
        every { Uri.parse(any()) } answers { fakeUri(firstArg()) }
        mockkStatic(MimeTypeMap::class)
        val mimeTypeMap: MimeTypeMap = mockk()
        every { MimeTypeMap.getSingleton() } returns mimeTypeMap
        every { mimeTypeMap.getMimeTypeFromExtension(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        unmockkStatic(MimeTypeMap::class)
    }

    @Test
    fun `a published note is still addressed by its private file`() {
        val mapped = note(publishedAddress = PUBLISHED_URI).toMediaFile()

        assertEquals(PRIVATE_URI, mapped.file.uri.toString())
    }

    @Test
    fun `a published note carries its published row alongside`() {
        val mapped = note(publishedAddress = PUBLISHED_URI).toMediaFile()

        assertTrue("a published note must report a second half to keep in step", mapped.isPublished)
        assertEquals(PUBLISHED_URI, mapped.publishedUri?.toString())
    }

    @Test
    fun `an unpublished note reports no second half`() {
        val mapped = note(publishedAddress = null).toMediaFile()

        assertFalse(mapped.isPublished)
        assertNull(mapped.publishedUri)
    }

    /** A row written with an empty address is the same absence as a null one, not a second half. */
    @Test
    fun `a blank published address is no second half`() {
        val mapped = note(publishedAddress = "   ").toMediaFile()

        assertFalse(mapped.isPublished)
        assertNull(mapped.publishedUri)
    }

    @Test
    fun `the mapped file carries the recording time rather than the file clock`() {
        val mapped = note(publishedAddress = null).toMediaFile()

        assertEquals(CREATED_AT_MILLIS, mapped.file.dateModified)
        assertEquals(DURATION_MILLIS, mapped.file.duration)
        assertEquals(SIZE_BYTES, mapped.file.size)
        assertEquals(FILE_NAME, mapped.file.name)
    }

    private fun note(publishedAddress: String?) = VoiceNote(
        id = NOTE_ID,
        fileName = FILE_NAME,
        absolutePath = ABSOLUTE_PATH,
        createdAtMillis = CREATED_AT_MILLIS,
        durationMillis = DURATION_MILLIS,
        sizeBytes = SIZE_BYTES,
        deliveryState = VoiceNoteDeliveryState.LOCAL_ONLY,
        publishedAddress = publishedAddress
    )

    /** `Uri` is a platform stub on the JVM, so the double answers only what the mapping reads back. */
    private fun fakeUri(value: String): Uri = mockk(relaxed = true) {
        every { this@mockk.toString() } returns value
    }

    private companion object {
        const val NOTE_ID = 7L
        const val FILE_NAME = "audio_260903_155943.m4a"
        val ABSOLUTE_PATH: String = File("/data/voice_notes", FILE_NAME).path
        const val PRIVATE_URI = "file:///data/voice_notes/audio_260903_155943.m4a"
        const val PUBLISHED_URI = "content://media/external/audio/media/42"
        const val CREATED_AT_MILLIS = 1_756_900_000_000L
        const val DURATION_MILLIS = 4_200L
        const val SIZE_BYTES = 65_536L
    }
}
