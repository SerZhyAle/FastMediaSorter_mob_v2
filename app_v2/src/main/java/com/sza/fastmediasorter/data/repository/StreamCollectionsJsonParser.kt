package com.sza.fastmediasorter.data.repository

import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
    val droppedCollections: Int
) {
    companion object {
        val EMPTY = ParsedStreamCollections(emptyList(), 0)
    }
}

/**
 * S2669: reads the `collections.json` entry of the stream-catalog archive.
 *
 * The publisher already refuses a malformed set, so this parser's job is to survive an unexpected
 * payload without costing the caller the bank it imported successfully in the same run: an unknown
 * `schemaVersion` yields an empty result, and a single collection missing its members or its `en` name
 * is dropped rather than made fatal.
 *
 * Deliberately free of Android framework and Room types beyond `org.json`, so the whole contract is
 * testable as plain Kotlin against a string.
 */
@Singleton
class StreamCollectionsJsonParser @Inject constructor() {

    fun parse(json: String): ParsedStreamCollections {
        val root = try {
            JSONObject(json)
        } catch (e: org.json.JSONException) {
            Timber.w(e, "Stream collections: payload is not a JSON object, ignoring it")
            return ParsedStreamCollections.EMPTY
        }

        val version = root.optInt("schemaVersion", -1)
        if (version != SCHEMA_VERSION) {
            // Not an error on our side: the producer is entitled to raise the version, and a client
            // that guessed at an unknown shape would be worse than one that shows today's screen.
            Timber.w("Stream collections: schemaVersion %d is not %d, ignoring the entry", version, SCHEMA_VERSION)
            return ParsedStreamCollections.EMPTY
        }

        val array = root.optJSONArray("collections") ?: return ParsedStreamCollections.EMPTY
        val parsed = ArrayList<ParsedStreamCollection>(array.length())
        var dropped = 0
        for (index in 0 until array.length()) {
            val collection = array.optJSONObject(index)
            if (collection == null) {
                dropped++
                continue
            }
            val id = collection.optString("id").trim()
            val names = collection.optJSONObject("names")
            val fallbackName = names?.optString(FALLBACK_LOCALE)?.trim().orEmpty()
            val members = readMembers(collection)
            if (id.isEmpty() || fallbackName.isEmpty() || members.isEmpty()) {
                dropped++
                continue
            }
            parsed += ParsedStreamCollection(
                id = id,
                sortOrder = collection.optInt("order", index),
                namesJson = names.toString(),
                memberUrls = members
            )
        }
        if (dropped > 0) {
            Timber.w("Stream collections: dropped %d incomplete collection(s) of %d", dropped, array.length())
        }
        return ParsedStreamCollections(parsed, dropped)
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
