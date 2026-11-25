package com.sza.fastmediasorter.data.notes

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName

/**
 * The wire spellings below are written by [NotePayloadCodec] through explicit keys, never by Gson reflection. The
 * @SerializedName pins exist so a future Gson call on these models cannot emit R8-renamed members or constant names
 * that differ from the item FH wire form.
 */
enum class NoteKind(val wire: String) {
    @SerializedName("text")
    TEXT("text"),

    @SerializedName("checklist")
    CHECKLIST("checklist"),
    ;

    companion object {
        fun fromWire(wire: String?): NoteKind? = entries.firstOrNull { it.wire == wire }
    }
}

data class NoteItem(
    @SerializedName("itemId") val itemId: String,
    @SerializedName("text") val text: String,
    @SerializedName("checked") val checked: Boolean,
    @SerializedName("position") val position: Int,
)

/**
 * The clear payload inside a note's box (DEVICE-EXCHANGE 1.13 item FH). [text] is set for [NoteKind.TEXT],
 * [items] for [NoteKind.CHECKLIST]; a null [title] is a note with no name, never a placeholder word.
 */
data class NotePayload(
    @SerializedName("title") val title: String?,
    @SerializedName("kind") val kind: NoteKind,
    @SerializedName("text") val text: String?,
    @SerializedName("items") val items: List<NoteItem>,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("formatVersion") val formatVersion: Int = NotePayloadCodec.FORMAT_VERSION,
) {
    // A note's words must never reach a log; the operator side of item FG sees no title or text either.
    override fun toString(): String = "NotePayload(kind=$kind, items=${items.size}, updatedAt=$updatedAt)"
}

sealed interface NoteDecode {
    data class Decoded(val payload: NotePayload) : NoteDecode

    /** A `formatVersion` above this build's or an unknown kind: shown `format-unsupported`, left untouched. */
    data object Unsupported : NoteDecode
    data object Malformed : NoteDecode
}

/** Encodes and decodes the item FH payload; a reader ignores members it does not know. */
object NotePayloadCodec {

    const val FORMAT_VERSION = 1

    /** The payload limit of item FH; a larger note stays on its device. */
    const val MAX_PAYLOAD_BYTES = 131072

    private const val KEY_FORMAT_VERSION = "formatVersion"
    private const val KEY_TITLE = "title"
    private const val KEY_KIND = "kind"
    private const val KEY_TEXT = "text"
    private const val KEY_ITEMS = "items"
    private const val KEY_ITEM_ID = "itemId"
    private const val KEY_CHECKED = "checked"
    private const val KEY_POSITION = "position"
    private const val KEY_CREATED_AT = "createdAt"
    private const val KEY_UPDATED_AT = "updatedAt"

    fun toJson(payload: NotePayload): JsonObject = JsonObject().apply {
        addProperty(KEY_FORMAT_VERSION, payload.formatVersion)
        add(KEY_TITLE, payload.title?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        addProperty(KEY_KIND, payload.kind.wire)
        when (payload.kind) {
            NoteKind.TEXT -> addProperty(KEY_TEXT, normaliseLineEnds(payload.text.orEmpty()))
            NoteKind.CHECKLIST -> add(KEY_ITEMS, itemsJson(payload.items))
        }
        addProperty(KEY_CREATED_AT, payload.createdAt)
        addProperty(KEY_UPDATED_AT, payload.updatedAt)
    }

    /** The UTF-8 bytes of [toJson]; the item FH limit is judged on these. */
    fun encode(payload: NotePayload): ByteArray = toJson(payload).toString().toByteArray(Charsets.UTF_8)

    fun fits(payload: NotePayload): Boolean = encode(payload).size <= MAX_PAYLOAD_BYTES

    fun decode(bytes: ByteArray): NoteDecode {
        val json = runCatching { JsonParser.parseString(String(bytes, Charsets.UTF_8)) }.getOrNull()
            ?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        return if (json == null) NoteDecode.Malformed else decode(json)
    }

    fun decode(json: JsonObject): NoteDecode {
        val version = numberOf(json, KEY_FORMAT_VERSION)?.toIntOrNull()
        val kindWire = stringOf(json, KEY_KIND)
        val kind = NoteKind.fromWire(kindWire)
        return when {
            version == null || version < FORMAT_VERSION || kindWire == null -> NoteDecode.Malformed
            version > FORMAT_VERSION || kind == null -> NoteDecode.Unsupported
            else -> payloadOf(json, kind)?.let(NoteDecode::Decoded) ?: NoteDecode.Malformed
        }
    }

    /** Item CB's rule: CRLF and a lone CR become LF. */
    fun normaliseLineEnds(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    private fun payloadOf(json: JsonObject, kind: NoteKind): NotePayload? {
        val stamps = stringOf(json, KEY_CREATED_AT)?.let { created ->
            stringOf(json, KEY_UPDATED_AT)?.let { updated -> created to updated }
        }
        val title = json.get(KEY_TITLE)
        val titleValid = title == null || title.isJsonNull || stringOf(json, KEY_TITLE) != null
        val text = stringOf(json, KEY_TEXT)?.let(::normaliseLineEnds)
        val items = if (kind == NoteKind.CHECKLIST) itemsOf(json) else emptyList()
        val bodyValid = titleValid && (kind != NoteKind.TEXT || text != null)
        return if (stamps == null || items == null || !bodyValid) {
            null
        } else {
            NotePayload(
                title = stringOf(json, KEY_TITLE),
                kind = kind,
                text = if (kind == NoteKind.TEXT) text else null,
                items = items,
                createdAt = stamps.first,
                updatedAt = stamps.second,
            )
        }
    }

    private fun itemsOf(json: JsonObject): List<NoteItem>? {
        val array = json.get(KEY_ITEMS)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return null
        val items = array.map { element -> element.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let(::itemOf) }
        return if (items.any { it == null }) null else items.filterNotNull().sortedBy(NoteItem::position)
    }

    private fun itemOf(json: JsonObject): NoteItem? {
        val itemId = stringOf(json, KEY_ITEM_ID)
        val text = stringOf(json, KEY_TEXT)
        val checked = json.get(KEY_CHECKED)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
            ?.takeIf(JsonPrimitive::isBoolean)?.asBoolean
        val state = checked?.let { done -> numberOf(json, KEY_POSITION)?.toIntOrNull()?.let { done to it } }
        return if (itemId == null || text == null || state == null) {
            null
        } else {
            NoteItem(itemId, text, state.first, state.second)
        }
    }

    private fun itemsJson(items: List<NoteItem>): JsonArray = JsonArray().apply {
        items.sortedBy(NoteItem::position).forEach { item ->
            add(
                JsonObject().apply {
                    addProperty(KEY_ITEM_ID, item.itemId)
                    addProperty(KEY_TEXT, item.text)
                    addProperty(KEY_CHECKED, item.checked)
                    addProperty(KEY_POSITION, item.position)
                },
            )
        }
    }

    private fun stringOf(json: JsonObject, key: String): String? =
        json.get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive?.takeIf(JsonPrimitive::isString)?.asString

    private fun numberOf(json: JsonObject, key: String): String? =
        json.get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive?.takeIf(JsonPrimitive::isNumber)?.asString
}
