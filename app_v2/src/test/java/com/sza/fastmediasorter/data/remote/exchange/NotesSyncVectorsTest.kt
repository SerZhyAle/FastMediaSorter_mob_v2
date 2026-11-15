package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.notes.NoteDecode
import com.sza.fastmediasorter.data.notes.NotePayloadCodec
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eVectorsGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The notes-sync vectors of DEVICE-EXCHANGE 1.13 item FM. The run writes `build/notes-sync-vectors/` for filing
 * under the catalog's `proposed-vectors/notes-sync-2026-11-14/`; the other tests hold the file to this client's
 * own parsers and to the e2e device keys, so a filed vector is one this program reads.
 */
class NotesSyncVectorsTest {

    private val root: JsonObject by lazy { NotesSyncVectorsGenerator.generate() }

    @Test
    fun `the generator is deterministic and writes the file for filing`() {
        val rendered = NotesSyncVectorsGenerator.render(root)
        File(OUTPUT_DIR).mkdirs()
        File("$OUTPUT_DIR/$OUTPUT_FILE").writeText(rendered)

        assertEquals(rendered, NotesSyncVectorsGenerator.render(NotesSyncVectorsGenerator.generate()))
    }

    @Test
    fun `every server frame parses as this client reads it`() {
        val server = frames(NotesSyncVectorsGenerator.SERVER)
        assertNotNull(ExchangeNotesEnvelopes.parseStored(server.getValue("stored-create")))
        assertEquals(2L, ExchangeNotesEnvelopes.parseChanged(server.getValue("changed")))
        val changes = ExchangeNotesEnvelopes.parseChanges(server.getValue("changes"))
        assertEquals(1, changes?.records?.size)
        assertNotNull(ExchangeNotesEnvelopes.parseConflict(server.getValue("note-conflict")))
        assertEquals(3L, ExchangeNotesEnvelopes.parseStored(server.getValue("stored-delete"))?.revision)
    }

    @Test
    fun `every client frame names its type and a valid note id`() {
        val client = frames(NotesSyncVectorsGenerator.CLIENT)
        assertEquals(ExchangeNotesEnvelopes.TYPE_NOTES_LIST, client.getValue("list-from-start").type)
        listOf("put-create", "put-stale", "put-delete").forEach { name ->
            val put = client.getValue(name)
            assertEquals(name, ExchangeNotesEnvelopes.TYPE_NOTES_PUT, put.type)
            assertTrue(name, ExchangeNotesEnvelopes.isValidNoteId(put.string(ExchangeNotesEnvelopes.KEY_NOTE_ID)))
        }
    }

    @Test
    fun `each box opens on a recipient to the payload sample it was sealed from`() {
        val tablet = E2eVectorsGenerator.self("tablet")
        val cases = mapOf(
            NotesSyncVectorsGenerator.TEXT_NOTE_ID to NotesSyncVectorsGenerator.textPayload,
            NotesSyncVectorsGenerator.CHECKLIST_NOTE_ID to NotesSyncVectorsGenerator.checklistPayload,
        )
        cases.forEach { (noteId, payload) ->
            val box = NotesSyncVectorsGenerator.box(noteId, payload, "note check")
            val opened = E2eSealedBox.open(box, E2eSealedBox.noteContext(noteId), tablet) { _, _ -> true }
            check(opened is E2eOpenResult.Opened) { "box of $noteId did not open: $opened" }
            assertEquals(NoteDecode.Decoded(payload), NotePayloadCodec.decode(opened.payload))
        }
    }

    private fun frames(direction: String): Map<String, DeviceExchangeEnvelope> =
        root.getAsJsonArray("frames").map { it.asJsonObject }
            .filter { it.get("direction").asString == direction }
            .associate { it.get("name").asString to DeviceExchangeEnvelope.decode(it.get("frame").toString()) }

    private companion object {
        const val OUTPUT_DIR = "build/notes-sync-vectors"
        const val OUTPUT_FILE = "notes-sync-v1.json"
    }
}
