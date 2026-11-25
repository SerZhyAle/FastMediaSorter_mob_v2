package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** One note record of the account's store (DEVICE-EXCHANGE 1.13 item FG); [sealed] is absent on a tombstone. */
data class NoteRecord(
    val noteId: String,
    val revision: Long,
    val storedAt: String?,
    val sourceDeviceId: String?,
    val deleted: Boolean,
    val sealed: JsonObject?,
) {
    override fun toString(): String =
        "NoteRecord(noteId=$noteId, revision=$revision, storedAt=$storedAt, deleted=$deleted, sealed=${sealed != null})"
}

/** The members of one `notes_changes` (item FI). */
data class NotesChanges(
    val notesRevision: Long,
    val notesHorizon: Long,
    val records: List<NoteRecord>,
    val more: Boolean,
    val full: Boolean,
)

/** The members of `notes_stored` (item FI). */
data class NoteStored(val noteId: String, val revision: Long, val storedAt: String?)

/** Wire envelopes of the notes store on the exchange server (DEVICE-EXCHANGE 1.13 item FI). */
object ExchangeNotesEnvelopes {

    const val TYPE_NOTES_LIST = "notes_list"
    const val TYPE_NOTES_CHANGES = "notes_changes"
    const val TYPE_NOTES_PUT = "notes_put"
    const val TYPE_NOTES_STORED = "notes_stored"
    const val TYPE_NOTES_CHANGED = "notes_changed"

    const val REASON_NOTE_CONFLICT = "note-conflict"
    const val REASON_LIMIT_REACHED = "limit-reached"

    const val KEY_SINCE = "since"
    const val KEY_NOTE_ID = "noteId"
    const val KEY_REVISION = "revision"
    const val KEY_BASE_REVISION = "baseRevision"
    const val KEY_STORED_AT = "storedAt"
    const val KEY_SOURCE_DEVICE_ID = "sourceDeviceId"
    const val KEY_DELETED = "deleted"
    const val KEY_SEALED = "sealed"
    const val KEY_NOTES_REVISION = "notesRevision"
    const val KEY_NOTES_HORIZON = "notesHorizon"
    const val KEY_RECORDS = "records"
    const val KEY_MORE = "more"
    const val KEY_FULL = "full"
    const val KEY_CURRENT = "current"

    private val NOTE_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    /** The lower-case 8-4-4-4-12 form of item FG; any other `noteId` is dropped by the parsers. */
    fun isValidNoteId(noteId: String?): Boolean = noteId != null && NOTE_ID.matches(noteId)

    fun listEnvelope(since: Long): DeviceExchangeEnvelope =
        DeviceExchangeEnvelope.of(TYPE_NOTES_LIST) { addProperty(KEY_SINCE, since.coerceAtLeast(0L)) }

    /** A put of [sealed] over [baseRevision], or a delete when [deleted]; a delete carries no box. */
    fun putEnvelope(noteId: String, baseRevision: Long, deleted: Boolean, sealed: JsonObject?): DeviceExchangeEnvelope {
        require(deleted || sealed != null) { "a live put needs a box" }
        return DeviceExchangeEnvelope.of(TYPE_NOTES_PUT) {
            addProperty(KEY_NOTE_ID, noteId)
            addProperty(KEY_BASE_REVISION, baseRevision)
            addProperty(KEY_DELETED, deleted)
            if (!deleted) add(KEY_SEALED, sealed)
        }
    }

    fun parseChanges(envelope: DeviceExchangeEnvelope): NotesChanges? {
        if (envelope.type != TYPE_NOTES_CHANGES) return null
        val records = envelope.members.get(KEY_RECORDS)?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?.mapNotNull { element -> element.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let(::recordOf) }
            .orEmpty()
            .sortedBy(NoteRecord::revision)
        return NotesChanges(
            notesRevision = longOf(envelope.members, KEY_NOTES_REVISION) ?: 0L,
            notesHorizon = longOf(envelope.members, KEY_NOTES_HORIZON) ?: 0L,
            records = records,
            more = envelope.boolean(KEY_MORE) == true,
            full = envelope.boolean(KEY_FULL) == true,
        )
    }

    fun parseStored(envelope: DeviceExchangeEnvelope): NoteStored? {
        val noteId = envelope.string(KEY_NOTE_ID)?.takeIf(::isValidNoteId)
        val revision = longOf(envelope.members, KEY_REVISION)
        return if (envelope.type != TYPE_NOTES_STORED || noteId == null || revision == null) {
            null
        } else {
            NoteStored(noteId, revision, envelope.string(KEY_STORED_AT))
        }
    }

    /** The `current` record of a `refused {reason:"note-conflict"}` (item FJ); null for any other envelope. */
    fun parseConflict(envelope: DeviceExchangeEnvelope): NoteRecord? =
        envelope.takeIf {
            it.type == DeviceExchangeEnvelope.TYPE_REFUSED &&
                it.string(DeviceExchangeEnvelope.KEY_REASON) == REASON_NOTE_CONFLICT
        }?.obj(KEY_CURRENT)?.let(::recordOf)

    /** The `notesRevision` of an unsolicited `notes_changed`; null for any other envelope. */
    fun parseChanged(envelope: DeviceExchangeEnvelope): Long? =
        envelope.takeIf { it.type == TYPE_NOTES_CHANGED }?.let { longOf(it.members, KEY_NOTES_REVISION) }

    /** A record of item FG; one with a malformed `noteId` or no `revision` is dropped, unknown members ignored. */
    fun recordOf(json: JsonObject): NoteRecord? {
        val noteId = stringOf(json, KEY_NOTE_ID)
        val revision = longOf(json, KEY_REVISION)
        return if (noteId == null || !isValidNoteId(noteId) || revision == null) {
            null
        } else {
            val deleted = json.get(KEY_DELETED)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
                ?.takeIf(JsonPrimitive::isBoolean)?.asBoolean == true
            NoteRecord(
                noteId = noteId,
                revision = revision,
                storedAt = stringOf(json, KEY_STORED_AT),
                sourceDeviceId = stringOf(json, KEY_SOURCE_DEVICE_ID),
                deleted = deleted,
                sealed = if (deleted) null else json.get(KEY_SEALED)?.takeIf(JsonElement::isJsonObject)?.asJsonObject,
            )
        }
    }

    private fun stringOf(json: JsonObject, key: String): String? =
        json.get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive?.takeIf(JsonPrimitive::isString)?.asString

    private fun longOf(json: JsonObject, key: String): Long? =
        json.get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
            ?.takeIf(JsonPrimitive::isNumber)?.asString?.toLongOrNull()
}
