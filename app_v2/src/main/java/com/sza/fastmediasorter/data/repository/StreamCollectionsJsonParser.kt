package com.sza.fastmediasorter.data.repository

import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2669: reads the `collections.json` entry of the stream-catalog archive.
 *
 * The publisher already refuses a malformed set, so this parser's job is to survive an unexpected
 * payload without costing the caller the bank it imported successfully in the same run: an unknown
 * `schemaVersion` or a malformed payload yields a REFUSED result, and a single collection missing its
 * members or its `en` name is dropped rather than made fatal.
 *
 * Refused and empty are different answers (STREAM-BANK 2.4 SB-A7): a valid entry with an empty
 * `collections` array is the publisher clearing the set, while a refusal must keep the stored set, so
 * the caller reads [ParsedStreamCollections.refused] before replacing anything.
 *
 * Deliberately free of Android framework and Room types beyond `org.json`, so the whole contract is
 * testable as plain Kotlin against a string. The result holders are nested members rather than
 * top-level types: the package's naming rule (S1884/S2103 lineage) charges top-level declarations
 * in `data.repository` an architectural suffix, and a parse result is not an architecture.
 */
@Singleton
class StreamCollectionsJsonParser @Inject constructor() {

    /** One curated collection as it arrived in the archive, before it reaches storage. */
    data class ParsedStreamCollection(
        val id: String,
        val sortOrder: Int,
        /** The locale map serialised back to JSON, stored verbatim so the set of locales stays open. */
        val namesJson: String,
        /** Stream urls in the curator's order; the list order IS the order. */
        val memberUrls: List<String>
    )

    /** Outcome of one parse: what survived, and how much was dropped for the log. */
    data class ParsedStreamCollections(
        val collections: List<ParsedStreamCollection>,
        val droppedCollections: Int,
        /** True when the whole entry was not understood; the stored set must then stay as it is. */
        val refused: Boolean = false
    ) {
        companion object {
            val REFUSED = ParsedStreamCollections(emptyList(), 0, refused = true)
        }
    }

    fun parse(json: String): ParsedStreamCollections = when (val root = readRoot(json)) {
        null -> ParsedStreamCollections.REFUSED
        else -> parseCollections(root)
    }

    /**
     * The payload root, or null when the whole entry must be ignored. An unknown `schemaVersion` is
     * not an error on our side: the producer is entitled to raise the version, and a client that
     * guessed at an unknown shape would be worse than one that shows today's screen.
     */
    private fun readRoot(json: String): JSONObject? {
        val root = runCatching { JSONObject(json) }
            .onFailure { e -> Timber.w(e, "Stream collections: payload is not a JSON object, ignoring it") }
            .getOrNull()
        val version = root?.optInt("schemaVersion", -1) ?: -1
        if (root != null && version != SCHEMA_VERSION) {
            Timber.w("Stream collections: schemaVersion %d is not %d, ignoring the entry", version, SCHEMA_VERSION)
            return null
        }
        return root
    }

    private fun parseCollections(root: JSONObject): ParsedStreamCollections {
        val array = root.optJSONArray("collections")
        if (array == null) {
            Timber.w("Stream collections: no collections array, ignoring the entry")
            return ParsedStreamCollections.REFUSED
        }
        val parsed = ArrayList<ParsedStreamCollection>(array.length())
        var dropped = 0
        for (index in 0 until array.length()) {
            val collection = readCollection(array.optJSONObject(index), index)
            if (collection == null) dropped++ else parsed += collection
        }
        if (dropped > 0) {
            Timber.w("Stream collections: dropped %d incomplete collection(s) of %d", dropped, array.length())
        }
        return ParsedStreamCollections(parsed, dropped)
    }

    /** One collection, or null when it is incomplete and must be dropped without failing the rest. */
    private fun readCollection(collection: JSONObject?, fallbackIndex: Int): ParsedStreamCollection? {
        if (collection == null) return null
        val id = collection.optString("id").trim()
        val names = collection.optJSONObject("names")
        val fallbackName = names?.optString(FALLBACK_LOCALE)?.trim().orEmpty()
        val members = readMembers(collection)
        val complete = id.isNotEmpty() && fallbackName.isNotEmpty() && members.isNotEmpty()
        return if (names == null || !complete) {
            null
        } else {
            ParsedStreamCollection(
                id = id,
                sortOrder = collection.optInt("order", fallbackIndex),
                namesJson = names.toString(),
                memberUrls = members
            )
        }
    }

    private fun readMembers(collection: JSONObject): List<String> {
        val array = collection.optJSONArray("members") ?: return emptyList()
        val urls = LinkedHashSet<String>(array.length())
        for (index in 0 until array.length()) {
            val url = array.optJSONObject(index)?.optString("url")?.trim().orEmpty()
            if (url.isNotEmpty()) urls += url
        }
        return urls.toList()
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val FALLBACK_LOCALE = "en"
    }
}
