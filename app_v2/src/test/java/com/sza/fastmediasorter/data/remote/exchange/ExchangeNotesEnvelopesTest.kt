package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notes frames of DEVICE-EXCHANGE 1.13 item FI: what the phone writes and what it reads back. */
class ExchangeNotesEnvelopesTest {

    @Test
    fun `list envelope carries since`() {
        val encoded = ExchangeNotesEnvelopes.listEnvelope(SINCE).encode()

        assertEquals("""{"schemaVersion":2,"type":"notes_list","since":17}""", encoded)
    }

    @Test
    fun `put envelope carries the box over its base revision`() {
        val box = JsonObject().apply { addProperty("v", 1) }

        val encoded = ExchangeNotesEnvelopes.putEnvelope(NOTE_A, BASE, deleted = false, sealed = box).encode()

        assertEquals(
            """{"schemaVersion":2,"type":"notes_put","noteId":"$NOTE_A","baseRevision":4,"deleted":false,""" +
                """"sealed":{"v":1}}""",
            encoded,
        )
    }

    @Test
    fun `delete envelope carries no box`() {
        val encoded = ExchangeNotesEnvelopes.putEnvelope(NOTE_A, BASE, deleted = true, sealed = null).encode()

        assertEquals(
            """{"schemaVersion":2,"type":"notes_put","noteId":"$NOTE_A","baseRevision":4,"deleted":true}""",
            encoded,
        )
    }

    @Test
    fun `changes parse two records ascending and keep more`() {
        val changes = requireNotNull(ExchangeNotesEnvelopes.parseChanges(decode(CHANGES)))

        assertEquals(NOTES_REVISION, changes.notesRevision)
        assertEquals(0L, changes.notesHorizon)
        assertTrue(changes.more)
        assertFalse(changes.full)
        assertEquals(listOf(NOTE_B, NOTE_A), changes.records.map { it.noteId })
        assertTrue(changes.records[0].deleted)
        assertNull(changes.records[0].sealed)
        assertEquals(1, changes.records[1].sealed?.get("v")?.asInt)
        assertEquals("dev-pc", changes.records[1].sourceDeviceId)
    }

    @Test
    fun `a record with a malformed noteId or no revision is dropped`() {
        val body = """{"schemaVersion":2,"type":"notes_changes","notesRevision":3,"notesHorizon":0,"records":[""" +
            """{"noteId":"NOT-A-UUID","revision":1},{"noteId":"$NOTE_A"},""" +
            """{"noteId":"$NOTE_B","revision":3,"deleted":true}],"more":false}"""

        val changes = requireNotNull(ExchangeNotesEnvelopes.parseChanges(decode(body)))

        assertEquals(listOf(NOTE_B), changes.records.map { it.noteId })
    }

    @Test
    fun `stored and conflict and changed parse`() {
        val stored = ExchangeNotesEnvelopes.parseStored(
            decode("""{"schemaVersion":2,"type":"notes_stored","noteId":"$NOTE_A","revision":9,"storedAt":"t"}"""),
        )
        val conflict = ExchangeNotesEnvelopes.parseConflict(
            decode(
                """{"schemaVersion":2,"type":"refused","reason":"note-conflict",""" +
                    """"current":{"noteId":"$NOTE_A","revision":8,"sealed":{"v":1}}}""",
            ),
        )
        val other = ExchangeNotesEnvelopes.parseConflict(
            decode("""{"schemaVersion":2,"type":"refused","reason":"limit-reached"}"""),
        )
        val changed = ExchangeNotesEnvelopes.parseChanged(
            decode("""{"schemaVersion":2,"type":"notes_changed","notesRevision":12}"""),
        )

        assertEquals(NoteStored(NOTE_A, 9L, "t"), stored)
        assertEquals(8L, conflict?.revision)
        assertNull(other)
        assertEquals(12L, changed)
    }

    private fun decode(body: String): DeviceExchangeEnvelope = DeviceExchangeEnvelope.decode(body)

    private companion object {
        const val SINCE = 17L
        const val BASE = 4L
        const val NOTES_REVISION = 7L
        const val NOTE_A = "0f8fad5b-d9cb-469f-a165-70867728950e"
        const val NOTE_B = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        val CHANGES = """{"schemaVersion":2,"type":"notes_changes","notesRevision":7,"notesHorizon":0,""" +
            """"records":[{"noteId":"$NOTE_A","revision":7,"storedAt":"2026-11-14T10:00:00Z",""" +
            """"sourceDeviceId":"dev-pc","sealed":{"v":1},"unknown":true},""" +
            """{"noteId":"$NOTE_B","revision":5,"deleted":true}],"more":true}"""
    }
}
