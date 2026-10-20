package com.sza.fastmediasorter.data.security.e2e

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One recipient of a box: a `deviceId` and the key this program trusts for it (item DG). */
data class E2eRecipient(val deviceId: String, val publicKey: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is E2eRecipient && other.deviceId == deviceId && other.publicKey.contentEquals(publicKey)

    override fun hashCode(): Int = 31 * deviceId.hashCode() + publicKey.contentHashCode()
}

/** The device that seals or opens: its own `deviceId` and key. */
class E2eSelf(val deviceId: String, val key: E2eDeviceKey)

/** Why a box opened nothing. Every reason is shown as `sealed-failed` (item DR); the name is for the log. */
enum class E2eOpenFailure { MALFORMED, UNKNOWN_VERSION, NO_ENTRY, UNTRUSTED_SENDER, BAD_TAG }

sealed interface E2eOpenResult {
    /** [contentKey] is `CK`: the clipboard derives its value key from it (item DN). */
    class Opened(val payload: JsonObject, val contentKey: ByteArray, val from: String, val fromKey: ByteArray) :
        E2eOpenResult

    data class Failed(val reason: E2eOpenFailure) : E2eOpenResult
}

/**
 * The sealed box of contract item DH: the payload under AES-256-GCM with a random content key,
 * the content key wrapped per recipient by HPKE Auth mode, the context bound in both as
 * associated data so a box cannot be moved from one record to another.
 */
object E2eSealedBox {

    const val MAX_RECIPIENTS = 40
    const val CONTENT_KEY_LENGTH = 32
    private val HPKE_INFO = E2eBytes.ascii("fmsx-e2e-v1")

    fun accessContext(resourceId: String): String = "access:$resourceId"

    fun grantContext(grantId: String): String = "grant:$grantId"

    fun broadcastContext(broadcastId: String): String = "broadcast:$broadcastId"

    const val CLIPBOARD_CONTEXT = "clipboard"

    /** Seals [payload]; the caller has already cut [recipients] to the 40 seen most recently. */
    fun seal(
        payload: JsonObject,
        context: String,
        sender: E2eSelf,
        recipients: List<E2eRecipient>,
        random: E2eRandom = E2eRandom.secure,
    ): JsonObject = sealWithKey(payload, context, sender, recipients, random).first

    /** As [seal], also returning `CK` for a caller that derives more keys from it (item DN). */
    fun sealWithKey(
        payload: JsonObject,
        context: String,
        sender: E2eSelf,
        recipients: List<E2eRecipient>,
        random: E2eRandom = E2eRandom.secure,
    ): Pair<JsonObject, ByteArray> {
        require(recipients.isNotEmpty()) { "a box needs a recipient" }
        return box(payload, context, sender, recipients, random)
    }

    /**
     * The registering box of a grant's first `grant` (item DJ): `keys` empty and the payload `{}`, so it carries no
     * secret and opens for nobody; it only lets the server hold the grant and name the guest's devices.
     */
    fun registration(context: String, sender: E2eSelf, random: E2eRandom = E2eRandom.secure): JsonObject =
        box(JsonObject(), context, sender, emptyList(), random).first

    /** True for a box that addresses no device - a grant's registering box, never opened (item DJ). */
    fun addressesNobody(box: JsonElement?): Boolean = parse(box)?.entries?.isEmpty() == true

    private fun box(
        payload: JsonObject,
        context: String,
        sender: E2eSelf,
        recipients: List<E2eRecipient>,
        random: E2eRandom,
    ): Pair<JsonObject, ByteArray> {
        require(recipients.size <= MAX_RECIPIENTS) { "a box addresses at most $MAX_RECIPIENTS recipients" }
        val aad = E2eBytes.utf8(context)
        val contentKey = random.next(CONTENT_KEY_LENGTH)
        val nonce = random.next(E2eAead.NONCE_LENGTH)
        val ciphertext = E2eAead.seal(contentKey, nonce, aad, E2eBytes.utf8(payload.toString()))
        val keys = JsonArray()
        recipients.forEach { recipient ->
            val wrapped = E2eHpke.sealAuth(recipient.publicKey, HPKE_INFO, aad, contentKey, sender.key.pair, random)
            keys.add(
                JsonObject().apply {
                    addProperty("to", recipient.deviceId)
                    addProperty("enc", E2eBytes.b64url(wrapped.enc))
                    addProperty("wk", E2eBytes.b64url(wrapped.ciphertext))
                },
            )
        }
        val box = JsonObject().apply {
            addProperty("v", E2eDeviceKey.VERSION)
            addProperty("from", sender.deviceId)
            addProperty("fromKey", sender.key.kem)
            addProperty("n", E2eBytes.b64url(nonce))
            addProperty("ct", E2eBytes.b64url(ciphertext))
            add("keys", keys)
        }
        return box to contentKey
    }

    /**
     * Opens [box] found in the place whose context is [expectedContext] - never a context read from
     * the box. [trusts] answers whether `fromKey` is the key this device trusts for `from`.
     */
    fun open(
        box: JsonElement?,
        expectedContext: String,
        self: E2eSelf,
        trusts: (deviceId: String, publicKey: ByteArray) -> Boolean,
    ): E2eOpenResult {
        val parsed = parse(box) ?: return E2eOpenResult.Failed(E2eOpenFailure.MALFORMED)
        return when {
            parsed.version != E2eDeviceKey.VERSION -> E2eOpenResult.Failed(E2eOpenFailure.UNKNOWN_VERSION)
            parsed.entryFor(self.deviceId) == null -> E2eOpenResult.Failed(E2eOpenFailure.NO_ENTRY)
            !trusts(parsed.from, parsed.fromKey) -> E2eOpenResult.Failed(E2eOpenFailure.UNTRUSTED_SENDER)
            else -> openTrusted(parsed, expectedContext, self)
        }
    }

    private fun openTrusted(parsed: ParsedBox, context: String, self: E2eSelf): E2eOpenResult {
        val aad = E2eBytes.utf8(context)
        val entry = requireNotNull(parsed.entryFor(self.deviceId))
        val contentKey = E2eHpke.openAuth(entry.enc, self.key.pair, HPKE_INFO, aad, entry.wk, parsed.fromKey)
            ?.takeIf { it.size == CONTENT_KEY_LENGTH }
        val clear = contentKey?.let { E2eAead.open(it, parsed.nonce, aad, parsed.ciphertext) }
        val payload = clear?.let { bytes ->
            runCatching { JsonParser.parseString(String(bytes, Charsets.UTF_8)) }.getOrNull()
                ?.takeIf { it.isJsonObject }?.asJsonObject
        }
        return if (contentKey == null || payload == null) {
            E2eOpenResult.Failed(E2eOpenFailure.BAD_TAG)
        } else {
            E2eOpenResult.Opened(payload, contentKey, parsed.from, parsed.fromKey)
        }
    }

    /** The `from` member without opening anything - for the guest's first-use trust of item DJ. */
    fun senderOf(box: JsonElement?): Pair<String, ByteArray>? = parse(box)?.let { it.from to it.fromKey }

    private class Entry(val to: String, val enc: ByteArray, val wk: ByteArray)

    private class ParsedBox(
        val version: Int,
        val from: String,
        val fromKey: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val entries: List<Entry>,
    ) {
        fun entryFor(deviceId: String): Entry? = entries.firstOrNull { it.to == deviceId }
    }

    private fun parse(box: JsonElement?): ParsedBox? = runCatching {
        val obj = requireNotNull(box?.takeIf { it.isJsonObject }?.asJsonObject)
        val entries = obj.getAsJsonArray("keys").map { element ->
            val entry = element.asJsonObject
            Entry(
                entry.get("to").asString,
                E2eBytes.fromB64url(entry.get("enc").asString),
                E2eBytes.fromB64url(entry.get("wk").asString),
            )
        }
        ParsedBox(
            version = obj.get("v").asInt,
            from = obj.get("from").asString,
            fromKey = E2eBytes.fromB64url(obj.get("fromKey").asString),
            nonce = E2eBytes.fromB64url(obj.get("n").asString).also { require(it.size == E2eAead.NONCE_LENGTH) },
            ciphertext = E2eBytes.fromB64url(obj.get("ct").asString),
            entries = entries,
        )
    }.getOrNull()
}
