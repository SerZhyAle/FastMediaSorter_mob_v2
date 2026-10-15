package com.sza.fastmediasorter.data.security.e2e

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** The clear members of a sealed clipboard value, from its box (contract DEVICE-EXCHANGE 27 item DN). */
class E2eClipboardClear(
    val kind: String,
    val mimeType: String?,
    val name: String?,
    val size: Long,
    val sha256: String,
)

/** How a clipboard value of the server reads on this device. */
sealed interface E2eClipboardRead {
    /** [sealedSize] and [sealedSha256] are the server's, over the sealed bytes; [contentKey] derives the value key. */
    class Opened(
        val clear: E2eClipboardClear,
        val sealedSize: Long,
        val sealedSha256: String,
        val contentKey: ByteArray,
    ) : E2eClipboardRead

    /** The box did not open, or a member is missing: `sealed-failed`. */
    data object Failed : E2eClipboardRead

    /** A clear kind from a sender without the capability: never placed (`e2e-missing`). */
    data object Clear : E2eClipboardRead
}

/**
 * The clipboard half of item DN shared by the phone and the watch: what the box carries and how a value from the
 * server is opened. The bytes are [E2eSegmentStream]'s.
 */
object E2eClipboard {

    const val KIND_SEALED = "sealed"
    const val KEY_KIND = "kind"
    const val KEY_SIZE = "size"
    const val KEY_SHA256 = "sha256"
    const val KEY_SEALED = "sealed"
    private const val KEY_MIME_TYPE = "mimeType"
    private const val KEY_NAME = "name"

    /** The box's payload: the members of 22 item BW for the clear value, with its size and SHA-256. */
    fun payload(kind: String, mimeType: String?, name: String?, size: Long, sha256: String): JsonObject =
        JsonObject().apply {
            addProperty(KEY_KIND, kind)
            mimeType?.let { addProperty(KEY_MIME_TYPE, it) }
            name?.let { addProperty(KEY_NAME, it) }
            addProperty(KEY_SIZE, size)
            addProperty(KEY_SHA256, sha256)
        }

    /**
     * Reads the members of a `clipboard-value` or of `clipboard-state`'s `value`. [self] null means this device has
     * no key, so nothing sealed opens here.
     */
    fun read(value: JsonObject, self: E2eSelf?, trusts: (String, ByteArray) -> Boolean): E2eClipboardRead {
        val kind = text(value, KEY_KIND)
        return when {
            kind != KIND_SEALED -> E2eClipboardRead.Clear
            self == null -> E2eClipboardRead.Failed
            else -> open(value, self, trusts)
        }
    }

    private fun open(value: JsonObject, self: E2eSelf, trusts: (String, ByteArray) -> Boolean): E2eClipboardRead {
        val opened = E2eSealedBox.open(value.get(KEY_SEALED), E2eSealedBox.CLIPBOARD_CONTEXT, self, trusts)
            as? E2eOpenResult.Opened
        val clear = opened?.payload?.let(::clearOf)
        val sealedSize = number(value, KEY_SIZE)?.takeIf { it > 0 }
        val sealedSha = text(value, KEY_SHA256)
        val read = if (clear != null && sealedSize != null && sealedSha != null) {
            opened?.let { E2eClipboardRead.Opened(clear, sealedSize, sealedSha, it.contentKey) }
        } else {
            null
        }
        return read ?: E2eClipboardRead.Failed
    }

    private fun clearOf(payload: JsonObject): E2eClipboardClear? {
        val kind = text(payload, KEY_KIND)
        val size = number(payload, KEY_SIZE)?.takeIf { it >= 1 }
        val sha = text(payload, KEY_SHA256)
        return if (kind == null || size == null || sha == null) {
            null
        } else {
            E2eClipboardClear(kind, text(payload, KEY_MIME_TYPE), text(payload, KEY_NAME), size, sha)
        }
    }

    private fun text(json: JsonObject, key: String): String? =
        json.get(key)?.takeIf(::isString)?.asString

    private fun number(json: JsonObject, key: String): Long? =
        json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private fun isString(element: JsonElement): Boolean = element.isJsonPrimitive && element.asJsonPrimitive.isString
}
