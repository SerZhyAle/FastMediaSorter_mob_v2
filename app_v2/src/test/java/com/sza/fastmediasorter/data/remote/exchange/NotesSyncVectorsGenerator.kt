package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.notes.NoteItem
import com.sza.fastmediasorter.data.notes.NoteKind
import com.sza.fastmediasorter.data.notes.NotePayload
import com.sza.fastmediasorter.data.notes.NotePayloadCodec
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eVectorsGenerator
import com.sza.fastmediasorter.data.security.e2e.FixedRandom

/**
 * Producer of the notes-sync vectors this product owes as the first implementation (DEVICE-EXCHANGE 1.13 item FM,
 * "Conformance"): frame transcripts of `notes_list`, `notes_put`, `notes_stored`, `note-conflict` and
 * `notes_changed`, and a clear payload of each kind of item FH. Client frames are built by [ExchangeNotesEnvelopes];
 * server frames are written as this client parses them, so they are a client's reading and not a server's
 * transcript. Boxes are sealed with [FixedRandom] to the device keys of `vectors/e2e-v1.json`, so a rerun is byte
 * for byte the same and a second product opens them with the private keys published there.
 */
object NotesSyncVectorsGenerator {

    const val TEXT_NOTE_ID = "0b7c1d2e-0000-4000-8000-00000000f001"
    const val CHECKLIST_NOTE_ID = "0b7c1d2e-0000-4000-8000-00000000f002"
    private const val CREATED_AT = "2026-11-14T10:00:00Z"
    private const val UPDATED_AT = "2026-11-14T10:05:00Z"
    private const val STORED_AT = "2026-11-14T10:05:01Z"
    private const val SENDER = "phone"
    private const val NOTES_HORIZON = 0L
    private const val FIRST_REVISION = 1L
    private const val SECOND_REVISION = 2L
    private const val THIRD_REVISION = 3L
    private val RECIPIENTS = listOf("phone", "tablet")

    val textPayload = NotePayload(
        title = "Shopping",
        kind = NoteKind.TEXT,
        text = "Milk\nBread\nКофе",
        items = emptyList(),
        createdAt = CREATED_AT,
        updatedAt = UPDATED_AT,
    )

    val checklistPayload = NotePayload(
        title = null,
        kind = NoteKind.CHECKLIST,
        text = null,
        items = listOf(
            NoteItem(itemId = "i1", text = "Pack the charger", checked = true, position = 0),
            NoteItem(itemId = "i2", text = "Взять зонт", checked = false, position = 1),
        ),
        createdAt = CREATED_AT,
        updatedAt = UPDATED_AT,
    )

    fun generate(): JsonObject = JsonObject().apply {
        addProperty("contract", "DEVICE-EXCHANGE")
        addProperty("item", "FM")
        addProperty("version", "1.13")
        addProperty("producer", "FastMediaSorter Android, ticket S4648")
        addProperty(
            "note",
            "Boxes open with the device keys of vectors/e2e-v1.json; server frames are this client's reading.",
        )
        add("payloads", payloads())
        add("frames", frames())
    }

    fun render(root: JsonObject): String =
        GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n"

    fun box(noteId: String, payload: NotePayload, seed: String): JsonObject =
        E2eSealedBox.seal(
            NotePayloadCodec.toJson(payload),
            E2eSealedBox.noteContext(noteId),
            E2eVectorsGenerator.self(SENDER),
            RECIPIENTS.map(E2eVectorsGenerator::recipient),
            FixedRandom(seed),
        )

    private fun payloads(): JsonArray = JsonArray().apply {
        add(sample("text", textPayload))
        add(sample("checklist", checklistPayload))
    }

    private fun sample(name: String, payload: NotePayload): JsonObject = JsonObject().apply {
        addProperty("name", name)
        add("payload", NotePayloadCodec.toJson(payload))
        addProperty("bytes", NotePayloadCodec.encode(payload).size)
    }

    private fun frames(): JsonArray {
        val textBox = box(TEXT_NOTE_ID, textPayload, "note text")
        val checklistBox = box(CHECKLIST_NOTE_ID, checklistPayload, "note checklist")
        return JsonArray().apply {
            add(frame("list-from-start", CLIENT, ExchangeNotesEnvelopes.listEnvelope(0L)))
            add(frame("put-create", CLIENT, ExchangeNotesEnvelopes.putEnvelope(TEXT_NOTE_ID, 0L, false, textBox)))
            add(frame("stored-create", SERVER, stored(TEXT_NOTE_ID, FIRST_REVISION)))
            add(frame("changed", SERVER, changed(SECOND_REVISION)))
            add(frame("list-since", CLIENT, ExchangeNotesEnvelopes.listEnvelope(FIRST_REVISION)))
            add(frame("changes", SERVER, changes(record(CHECKLIST_NOTE_ID, SECOND_REVISION, checklistBox))))
            add(frame("put-stale", CLIENT, ExchangeNotesEnvelopes.putEnvelope(TEXT_NOTE_ID, 0L, false, textBox)))
            add(frame("note-conflict", SERVER, conflict(record(TEXT_NOTE_ID, FIRST_REVISION, textBox))))
            val delete = ExchangeNotesEnvelopes.putEnvelope(TEXT_NOTE_ID, FIRST_REVISION, true, null)
            add(frame("put-delete", CLIENT, delete))
            add(frame("stored-delete", SERVER, stored(TEXT_NOTE_ID, THIRD_REVISION)))
        }
    }

    private fun frame(name: String, direction: String, envelope: DeviceExchangeEnvelope): JsonObject =
        JsonObject().apply {
            addProperty("name", name)
            addProperty("direction", direction)
            add("frame", JsonParser.parseString(envelope.encode()))
        }

    private fun record(noteId: String, revision: Long, sealed: JsonObject?): JsonObject = JsonObject().apply {
        addProperty(ExchangeNotesEnvelopes.KEY_NOTE_ID, noteId)
        addProperty(ExchangeNotesEnvelopes.KEY_REVISION, revision)
        addProperty(ExchangeNotesEnvelopes.KEY_STORED_AT, STORED_AT)
        addProperty(ExchangeNotesEnvelopes.KEY_SOURCE_DEVICE_ID, E2eVectorsGenerator.deviceId(SENDER))
        addProperty(ExchangeNotesEnvelopes.KEY_DELETED, sealed == null)
        sealed?.let { add(ExchangeNotesEnvelopes.KEY_SEALED, it) }
    }

    private fun stored(noteId: String, revision: Long) =
        DeviceExchangeEnvelope.of(ExchangeNotesEnvelopes.TYPE_NOTES_STORED) {
            addProperty(ExchangeNotesEnvelopes.KEY_NOTE_ID, noteId)
            addProperty(ExchangeNotesEnvelopes.KEY_REVISION, revision)
            addProperty(ExchangeNotesEnvelopes.KEY_STORED_AT, STORED_AT)
        }

    private fun changed(revision: Long) = DeviceExchangeEnvelope.of(ExchangeNotesEnvelopes.TYPE_NOTES_CHANGED) {
        addProperty(ExchangeNotesEnvelopes.KEY_NOTES_REVISION, revision)
    }

    private fun changes(record: JsonObject) = DeviceExchangeEnvelope.of(ExchangeNotesEnvelopes.TYPE_NOTES_CHANGES) {
        addProperty(ExchangeNotesEnvelopes.KEY_NOTES_REVISION, SECOND_REVISION)
        addProperty(ExchangeNotesEnvelopes.KEY_NOTES_HORIZON, NOTES_HORIZON)
        add(ExchangeNotesEnvelopes.KEY_RECORDS, JsonArray().apply { add(record) })
        addProperty(ExchangeNotesEnvelopes.KEY_MORE, false)
        addProperty(ExchangeNotesEnvelopes.KEY_FULL, false)
    }

    private fun conflict(current: JsonObject) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_REFUSED) {
        addProperty(DeviceExchangeEnvelope.KEY_REASON, ExchangeNotesEnvelopes.REASON_NOTE_CONFLICT)
        add(ExchangeNotesEnvelopes.KEY_CURRENT, current)
    }

    const val CLIENT = "client-to-server"
    const val SERVER = "server-to-client"
}
