package com.sza.fastmediasorter.ui.streams.helpers

import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale

/**
 * S2669: turns a collection's stored locale map into the one name this user reads.
 *
 * The catalog stores `namesJson` verbatim so a newly published locale reaches the user with the next
 * import and without an app release; that means resolution cannot happen in the data layer, which does
 * not know the app locale. The fall-through is full tag, then language-only tag, then `en` - the
 * publisher guarantees `en`, so the last rung normally ends the search.
 *
 * The collection id is returned only when the map is unusable. It is an opaque slug and unreadable on
 * screen, so that outcome is logged: it means the delivered payload is malformed, not that the user
 * picked an exotic locale.
 */
object StreamCollectionNameResolver {

    private const val FALLBACK_TAG = "en"

    fun resolve(collectionId: String, namesJson: String, locale: Locale): String {
        val names = parseNames(collectionId, namesJson)
        val resolved = candidateTags(locale)
            .firstNotNullOfOrNull { tag -> names[tag]?.takeIf(String::isNotBlank) }
        if (resolved == null) {
            Timber.w("Stream collection %s carries no usable display name; showing its id", collectionId)
        }
        return resolved ?: collectionId
    }

    /** BCP-47 tags are case-insensitive, and a payload may spell a region in either case. */
    private fun candidateTags(locale: Locale): List<String> = listOf(
        locale.toLanguageTag(),
        locale.language,
        FALLBACK_TAG,
    ).map { it.lowercase(Locale.ROOT) }

    private fun parseNames(collectionId: String, namesJson: String): Map<String, String> {
        val parsed = try {
            JSONObject(namesJson)
        } catch (e: JSONException) {
            // A malformed payload is a publisher defect, not a user condition - it is why the caller
            // will fall back to the raw id, so the reason has to survive in the log.
            Timber.w(e, "Stream collection %s has a malformed names payload", collectionId)
            null
        }
        return parsed?.keys()?.asSequence()
            ?.associate { key -> key.lowercase(Locale.ROOT) to parsed.optString(key) }
            .orEmpty()
    }
}
