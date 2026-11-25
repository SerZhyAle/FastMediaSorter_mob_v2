package com.sza.fastmediasorter.data.broadcast

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.lang.reflect.Type

/**
 * S4160: the stored form of a descriptor's `endpoints` list on a stream row. Kept verbatim, unknown
 * transports included, so a later app that plays `P2P` finds it already saved; the player skips what it
 * cannot use.
 *
 * S4675: a stored list never holds a relay group key or a tunnel pin in clear text. [protect] moves both into a
 * `seal.box` that only this device's [BroadcastSecretBox] opens, in the same column and the same row, so one
 * statement replaces a row whole. [decode] opens the boxes; a box that will not open leaves the seal without keys,
 * which no consumer plays - the row and its LAN endpoints stay, and nothing falls back to clear server bytes.
 * [encode] still writes the clear form: it is the canonical in-memory text a descriptor compares and travels as, and
 * a write to the table goes through [protect] first.
 */
object BroadcastEndpointCodec {

    private const val SEAL = "seal"
    private const val BOX = "box"
    private val SECRET_MEMBERS = listOf("key", "pin")

    private val gson = Gson()
    private val endpointListType: Type =
        TypeToken.getParameterized(List::class.java, BroadcastEndpointDto::class.java).type

    /** The box every default call uses; a test installs a fake, production never reassigns it. */
    @Volatile
    var secretBox: BroadcastSecretBox = KeystoreBroadcastSecretBox

    /** What [protect] decided for one stored value. */
    sealed interface Protection {

        /** Nothing in the value is a clear secret: absent, empty, unreadable or already protected. */
        data object Unchanged : Protection

        /** [value] is the stored text to write instead; it carries no clear secret. */
        class Protected(val value: String) : Protection

        /** A secret could not be sealed and read back; the caller must not write the clear value. */
        data object Failed : Protection
    }

    /** Null for an absent or empty list, so a legacy descriptor leaves the column null. Never carries a `box`. */
    fun encode(endpoints: List<BroadcastEndpointDto>?): String? =
        endpoints?.takeIf { it.isNotEmpty() }
            ?.map { endpoint -> endpoint.copy(seal = endpoint.seal?.copy(box = null)) }
            ?.let { gson.toJson(it, endpointListType) }

    /**
     * Null for a null column or a value that is not a list of endpoints - the row then plays its `url`. A sealed
     * endpoint comes back with its keys opened by [box], or with none when its box cannot be opened.
     */
    fun decode(stored: String?, box: BroadcastSecretBox = secretBox): List<BroadcastEndpointDto>? =
        read(stored, box)

    /**
     * Like [decode] but never opens a box: a seal keeps its version only. For a caption or a decision that needs the
     * transport and the address, so a list row bound on screen costs no Keystore call.
     */
    fun decodeMetadata(stored: String?): List<BroadcastEndpointDto>? = read(stored, null)

    /**
     * Whether [stored] would write a clear secret: some `seal` still holds a `key` or a `pin`. A migration reads this
     * to find the rows it owes, a write guard to refuse them.
     */
    fun holdsClearSecret(stored: String?): Boolean = parseArray(stored)?.let(::clearSeals)?.isNotEmpty() == true

    /**
     * The value to store for [stored]: every clear secret sealed by [box] and read back before it replaces the clear
     * one, so a box that cannot be opened again is never written. Members this app does not model are kept as they are.
     */
    fun protect(stored: String?, box: BroadcastSecretBox = secretBox): Protection {
        val endpoints = parseArray(stored)
        val clear = endpoints?.let(::clearSeals).orEmpty()
        return when {
            endpoints == null || clear.isEmpty() -> Protection.Unchanged
            clear.all { lock(it, box) } -> Protection.Protected(endpoints.toString())
            else -> Protection.Failed
        }
    }

    /**
     * Whether two stored values describe the same endpoints, boxed secrets compared by what they hold. The sealing is
     * randomised, so the text of one list differs from write to write.
     */
    fun sameEndpoints(first: String?, second: String?, box: BroadcastSecretBox = secretBox): Boolean {
        val a = decode(first, box)
        val b = decode(second, box)
        return a == b && (a != null || first.orEmpty() == second.orEmpty())
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun read(stored: String?, box: BroadcastSecretBox?): List<BroadcastEndpointDto>? {
        val tree = parseArray(stored) ?: return null
        openSeals(tree, box)
        val decoded: List<BroadcastEndpointDto?>? = try {
            gson.fromJson(tree, endpointListType)
        } catch (e: JsonParseException) {
            // The stored text carries keys, so only the class is logged.
            Timber.w("BroadcastEndpointCodec: stored endpoint list is unreadable - %s", e.javaClass.simpleName)
            null
        }
        // Gson fills a missing non-null member with null despite the Kotlin type; such an entry is unusable.
        return decoded?.filterNotNull()?.filter { !it.url.isNullOrBlank() }?.mapNotNull(::withTransport)
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseArray(stored: String?): JsonArray? {
        if (stored.isNullOrBlank()) return null
        val tree = try {
            JsonParser.parseString(stored)
        } catch (e: JsonParseException) {
            Timber.w("BroadcastEndpointCodec: stored endpoint list is unreadable - %s", e.javaClass.simpleName)
            null
        }
        return tree?.takeIf { it.isJsonArray }?.asJsonArray
    }

    private fun sealOf(endpoint: JsonElement): JsonObject? =
        (endpoint as? JsonObject)?.get(SEAL)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun clearSeals(endpoints: JsonArray): List<JsonObject> =
        endpoints.mapNotNull(::sealOf).filter { seal -> SECRET_MEMBERS.any(seal::has) }

    private fun lock(seal: JsonObject, box: BroadcastSecretBox): Boolean {
        val secrets = JsonObject()
        SECRET_MEMBERS.forEach { name -> seal.remove(name)?.let { secrets.add(name, it) } }
        val plain = secrets.toString()
        val sealed = box.seal(plain)?.takeIf { box.open(it) == plain }
        sealed?.let { seal.addProperty(BOX, it) }
        return sealed != null
    }

    // A `box` never reaches a DTO: it is opened into the seal's own members, or dropped when it will not open.
    private fun openSeals(endpoints: JsonArray, box: BroadcastSecretBox?) {
        var unopened = 0
        for (seal in endpoints.mapNotNull(::sealOf)) {
            val sealed = seal.remove(BOX)?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            if (box != null && !restore(seal, box.open(sealed))) unopened++
        }
        if (unopened > 0) Timber.w("BroadcastEndpointCodec: %d stored endpoint secret(s) could not be opened", unopened)
    }

    private fun restore(seal: JsonObject, plain: String?): Boolean {
        val secrets = plain?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
            ?.takeIf { it.isJsonObject }?.asJsonObject
        for (name in SECRET_MEMBERS) {
            val value = secrets?.get(name)?.takeIf { it.isJsonPrimitive } ?: continue
            seal.add(name, value)
        }
        return secrets != null
    }

    // LIVE-BROADCAST item W: an absent `transport` is read from the url's scheme, `rtsp` or `http` only. Any other
    // scheme, or a `seal`, may be a relay or a tunnel, which is never played as clear bytes, so the entry is skipped.
    @Suppress("SENSELESS_COMPARISON")
    private fun withTransport(endpoint: BroadcastEndpointDto): BroadcastEndpointDto? {
        if (endpoint.transport != null) return endpoint
        val scheme = endpoint.url.substringBefore("://", missingDelimiterValue = "").lowercase()
        val transport = SCHEME_TRANSPORTS[scheme]?.takeIf { endpoint.seal == null }
        return transport?.let { endpoint.copy(transport = it) }
    }

    private val SCHEME_TRANSPORTS = mapOf("rtsp" to "RTSP", "http" to "HTTP")
}
