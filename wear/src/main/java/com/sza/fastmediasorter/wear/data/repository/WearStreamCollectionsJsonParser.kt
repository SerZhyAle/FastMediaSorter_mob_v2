package com.sza.fastmediasorter.wear.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.wear.domain.model.WearStreamCollection
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2669: reads the `collections.json` entry of the stream-catalog archive on the watch.
 *
 * The rules are the phone parser's, deliberately: an unknown `schemaVersion` yields an empty result
 * (the producer is entitled to raise the version, and guessing at an unknown shape would be worse
 * than showing today's screen), and a single collection missing its members or its `en` name is
 * dropped rather than fatal, so a malformed payload never costs the bank imported in the same run.
 * The two parsers live in different modules with nothing else forcing them to agree, which is why
 * the mirrored test carries the same cases over the same payload text.
 *
 * Gson rather than `org.json`: the watch stores what this parse produces back out through Gson, and
 * the JVM-side unit test then needs no Robolectric to run it.
 */
@Singleton
class WearStreamCollectionsJsonParser @Inject constructor() {

    fun parse(json: String): List<WearStreamCollection> = when (val root = readRoot(json)) {
        null -> emptyList()
        else -> parseCollections(root)
    }

    /**
     * The payload root, or null when the whole entry must be ignored. An unknown `schemaVersion` is
     * not an error on our side: the producer is entitled to raise the version, and a client that
     * guessed at an unknown shape would be worse than one that shows today's screen.
     */
    private fun readRoot(json: String): JsonObject? {
        val root = runCatching { JsonParser.parseString(json) }
            .onFailure { e -> Timber.w(e, "Wear stream collections: payload is not a JSON object, ignoring it") }
            .getOrNull()
        val objectRoot = root?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        val version = objectRoot?.primitiveInt("schemaVersion") ?: -1
        if (objectRoot != null && version != SCHEMA_VERSION) {
            Timber.w(
                "Wear stream collections: schemaVersion %d is not %d, ignoring the entry",
                version,
                SCHEMA_VERSION
            )
            return null
        }
        return objectRoot
    }

    private fun parseCollections(root: JsonObject): List<WearStreamCollection> {
        val array = root.get("collections")?.takeIf(JsonElement::isJsonArray)?.asJsonArray
            ?: return emptyList()
        val parsed = ArrayList<WearStreamCollection>(array.size())
        var dropped = 0
        for (element in array) {
            val collection = readCollection(element)
            if (collection == null) dropped++ else parsed += collection
        }
        if (dropped > 0) {
            Timber.w("Wear stream collections: dropped %d incomplete collection(s) of %d", dropped, array.size())
        }
        // Delivery order, ties by id: the repository stores what it is handed and the picker reads
        // top to bottom, so the curator's `order` reaches the wearer without a second sort anywhere.
        return parsed.sortedWith(compareBy<WearStreamCollection> { it.sortOrder }.thenBy { it.id })
    }

    /** One collection, or null when it is incomplete and must be dropped without failing the rest. */
    private fun readCollection(element: JsonElement): WearStreamCollection? {
        if (!element.isJsonObject) return null
        val collection = element.asJsonObject
        val id = collection.primitiveString("id")?.trim().orEmpty()
        val names = readNames(collection.get("names"))
        val members = readMembers(collection.get("members"))
        val complete = id.isNotEmpty() && names.containsKey(FALLBACK_LOCALE) && members.isNotEmpty()
        return if (!complete) {
            null
        } else {
            WearStreamCollection(
                id = id,
                sortOrder = collection.primitiveInt("order") ?: 0,
                names = names,
                memberUrls = members
            )
        }
    }

    private fun readNames(element: JsonElement?): Map<String, String> {
        if (element == null || !element.isJsonObject) return emptyMap()
        val names = LinkedHashMap<String, String>(element.asJsonObject.size())
        for ((tag, value) in element.asJsonObject.entrySet()) {
            if (value.isJsonPrimitive) names[tag.lowercase()] = value.asString
        }
        return names
    }

    private fun readMembers(element: JsonElement?): List<String> {
        if (element == null || !element.isJsonArray) return emptyList()
        val urls = LinkedHashSet<String>(element.asJsonArray.size())
        for (member in element.asJsonArray) {
            val url = member.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?.primitiveString("url")
                ?.trim()
                .orEmpty()
            if (url.isNotEmpty()) urls += url
        }
        return urls.toList()
    }

    private fun JsonObject.primitiveInt(member: String): Int? =
        get(member)?.takeIf(JsonElement::isJsonPrimitive)?.takeIf { it.asJsonPrimitive.isNumber }?.asInt

    private fun JsonObject.primitiveString(member: String): String? =
        get(member)?.takeIf(JsonElement::isJsonPrimitive)?.takeIf { it.asJsonPrimitive.isString }?.asString

    private companion object {
        const val SCHEMA_VERSION = 1
        const val FALLBACK_LOCALE = "en"
    }
}
