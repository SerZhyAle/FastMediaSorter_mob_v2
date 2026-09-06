package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.repository.FakeVoiceNoteRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2626: the pass that brings stored voice-note titles onto the current UI language.
 *
 * It has no screen of its own and its failure mode is silence - a title that quietly stays in the
 * language of the day it was recorded - so the behaviours that decide whether it runs at all, and
 * what it records afterwards, are only observable here.
 */
class RefreshVoiceNoteTitlesUseCaseTest {

    /** Stands in for the DataStore-backed marker, which needs the framework the JVM suite lacks. */
    private class TagStore(var tag: String? = null) {

        val writes: MutableList<String> = mutableListOf()

        fun write(tag: String) {
            this.tag = tag
            writes += tag
        }
    }

    /** Records what was asked of the shared collection, and can refuse a chosen address. */
    private class RecordingWriter(private val refuse: Set<String> = emptySet()) {

        val written: MutableList<Pair<String, String>> = mutableListOf()

        fun retitle(address: String, title: String): Boolean {
            written += address to title
            return address !in refuse
        }
    }

    private fun note(id: Long, publishedAddress: String?): VoiceNote = VoiceNote(
        id = id,
        fileName = "audio_260903_15594$id.m4a",
        absolutePath = "/notes/audio_260903_15594$id.m4a",
        createdAtMillis = CREATED_AT_MILLIS + id,
        durationMillis = 1_000L,
        sizeBytes = 2_048L,
        deliveryState = VoiceNoteDeliveryState.LOCAL_ONLY,
        publishedAddress = publishedAddress
    )

    private fun useCase(
        notes: List<VoiceNote>,
        store: TagStore,
        writer: RecordingWriter
    ) = RefreshVoiceNoteTitlesUseCase(
        noteRepository = FakeVoiceNoteRepository(notes),
        readTag = { store.tag },
        writeTag = { tag -> store.write(tag) },
        retitle = writer::retitle
    )

    @Test
    fun `titles already written under the active language are left alone`() = runTest {
        val store = TagStore(tag = "ru")
        val writer = RecordingWriter()

        useCase(listOf(note(1L, "content://audio/1")), store, writer)("ru")

        assertTrue("a matching tag must not open the note index", writer.written.isEmpty())
        assertTrue("a matching tag must not rewrite the marker", store.writes.isEmpty())
    }

    @Test
    fun `a changed language rewrites every published note and skips the unpublished ones`() = runTest {
        val store = TagStore(tag = "ru")
        val writer = RecordingWriter()
        val notes = listOf(
            note(1L, "content://audio/1"),
            note(2L, null),
            note(3L, "   "),
            note(4L, "content://audio/4")
        )

        useCase(notes, store, writer)("uk")

        assertEquals(
            "only the notes carrying a published address are addressable",
            listOf("content://audio/1", "content://audio/4"),
            writer.written.map { it.first }
        )
        assertTrue("each row must be given a non-empty title", writer.written.all { it.second.isNotBlank() })
    }

    @Test
    fun `the marker records the new language once the walk is over`() = runTest {
        val store = TagStore(tag = "ru")

        useCase(listOf(note(1L, "content://audio/1")), store, RecordingWriter())("uk")

        assertEquals("uk", store.tag)
        assertEquals("the marker is written exactly once", listOf("uk"), store.writes)
    }

    @Test
    fun `a refused row leaves the marker behind so the next start walks again`() = runTest {
        val store = TagStore(tag = "ru")
        val writer = RecordingWriter(refuse = setOf("content://audio/1"))
        val notes = listOf(note(1L, "content://audio/1"), note(2L, "content://audio/2"))

        useCase(notes, store, writer)("uk")

        assertEquals("a refusal must not stop the remaining rows", 2, writer.written.size)
        assertEquals("the marker must not move past a row still in the old language", "ru", store.tag)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun `an absent marker rewrites the notes a previous build published`() = runTest {
        val store = TagStore(tag = null)
        val writer = RecordingWriter()

        useCase(listOf(note(1L, "content://audio/1")), store, writer)("uk")

        assertEquals("an unknown language must be treated as a mismatch", 1, writer.written.size)
        assertEquals("uk", store.tag)
    }

    @Test
    fun `an empty note index records the language without writing a row`() = runTest {
        val store = TagStore(tag = "ru")
        val writer = RecordingWriter()

        useCase(emptyList(), store, writer)("uk")

        assertTrue(writer.written.isEmpty())
        assertEquals("a watch that never recorded still records the language it is now on", "uk", store.tag)
    }

    private companion object {
        const val CREATED_AT_MILLIS = 1_756_900_000_000L
    }
}
