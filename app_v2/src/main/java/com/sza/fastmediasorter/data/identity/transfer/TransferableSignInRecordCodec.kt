package com.sza.fastmediasorter.data.identity.transfer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes a [TransferableSignInRecord] to the bytes handed to the transfer mechanism (S2101).
 *
 * Written and read by different builds of the app - the reader runs on the new device and may be
 * newer or older than the writer - so decoding is tolerant in both directions: an unknown field is
 * dropped, an entry whose kind this build does not know is dropped while its siblings are kept, and
 * malformed input yields null instead of an exception. Restoration is required to be silent, and an
 * exception on the first launch after a migration is the opposite of that.
 */
@Singleton
class TransferableSignInRecordCodec @Inject constructor() {

    fun encode(record: TransferableSignInRecord): ByteArray {
        val entries = JsonArray()
        record.entries.forEach { entry ->
            val payload = JsonObject()
            entry.payload.forEach { (key, value) -> payload.addProperty(key, value) }
            val json = JsonObject()
            json.addProperty(FIELD_PROVIDER_KEY, entry.providerKey)
            json.addProperty(FIELD_KIND, entry.kind.name)
            json.add(FIELD_PAYLOAD, payload)
            entries.add(json)
        }
        val root = JsonObject()
        root.addProperty(FIELD_SCHEMA_VERSION, record.schemaVersion)
        root.addProperty(FIELD_WRITTEN_AT, record.writtenAt)
        root.add(FIELD_ENTRIES, entries)
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): TransferableSignInRecord? = runCatching {
        val root = JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
        val entries = root.getAsJsonArray(FIELD_ENTRIES) ?: JsonArray()
        TransferableSignInRecord(
            schemaVersion = root.get(FIELD_SCHEMA_VERSION).asInt,
            writtenAt = root.get(FIELD_WRITTEN_AT).asLong,
            entries = entries.mapNotNull { decodeEntry(it.asJsonObject) }
        )
    }.getOrNull()

    private fun decodeEntry(json: JsonObject): TransferableSignInRecord.Entry? = runCatching {
        val kindName = json.get(FIELD_KIND).asString
        // An unrecognised kind means the writing build knew a category this one does not. Dropping
        // the entry keeps its siblings usable; guessing a kind would hand a secret to code that
        // expects an envelope.
        val kind = TransferableSignInRecord.Kind.entries.firstOrNull { it.name == kindName }
        val payloadJson = json.getAsJsonObject(FIELD_PAYLOAD)
        val payload = payloadJson.keySet().associateWith { payloadJson.get(it).asString }
        kind?.let {
            TransferableSignInRecord.Entry(
                providerKey = json.get(FIELD_PROVIDER_KEY).asString,
                kind = it,
                payload = payload
            )
        }
    }.getOrNull()

    private companion object {
        const val FIELD_SCHEMA_VERSION = "schemaVersion"
        const val FIELD_WRITTEN_AT = "writtenAt"
        const val FIELD_ENTRIES = "entries"
        const val FIELD_PROVIDER_KEY = "providerKey"
        const val FIELD_KIND = "kind"
        const val FIELD_PAYLOAD = "payload"
    }
}
