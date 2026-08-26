package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName
import java.net.URI
import java.util.Locale

/**
 * S1846: one favourite, kept as a record rather than as a `sourceId:filePath` string.
 *
 * The string form could be listed but never reopened: it carried no name to draw, no kind to pick a player
 * with, and nothing a player could resolve. A row that only shows a path is the dead end this ticket exists
 * to remove, one screen deeper.
 *
 * **Identity is [sourceId] plus [filePath], and nothing else.** That pair is what the delta sent to the phone
 * keys off, what `isFavorite` answers by, and what the legacy string form encoded - so a record and an old
 * string describing the same file are the same favourite, and the reader must not list both.
 *
 * [mediaType] and [displayName] are presentation, not identity: a legacy entry has neither and is still a
 * valid favourite, drawn by the last segment of its path.
 */
data class WearFavoriteRecord(
    @SerializedName("sourceId") val sourceId: String,
    @SerializedName("filePath") val filePath: String,
    @SerializedName("displayName") val displayName: String,
    // A coarse family such as image, video or audio, or null when this build never learned the kind.
    // Written as a line comment on purpose: the wildcard form of a mime type ends a block comment early.
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("itemKind") val itemKind: String? = null
) {

    /** The pair that decides sameness, joined the way the legacy store already joined it. */
    val identity: String get() = "$sourceId$IDENTITY_SEPARATOR$filePath"

    companion object {

        private const val IDENTITY_SEPARATOR = ":"

        /**
         * Reads a favourite written before this ticket.
         *
         * The separator is split on FIRST occurrence only: a file path holds colons of its own, and
         * splitting on all of them would rewrite the path and lose the file.
         */
        fun fromLegacyKey(key: String): WearFavoriteRecord? {
            val separator = key.indexOf(IDENTITY_SEPARATOR)
            if (separator <= 0 || separator == key.lastIndex) {
                return null
            }
            val path = key.substring(separator + 1)
            return WearFavoriteRecord(
                sourceId = key.substring(0, separator),
                filePath = path,
                displayName = path.substringAfterLast('/').ifBlank { path }
            )
        }
    }
}

/**
 * S1846: the one rule for what a favourite's source id means, cited by every writer.
 *
 * Before this it meant two different things. The image viewer wrote the category `network`; the audio
 * player wrote `uri.host`. A favourite could therefore be stored under one spelling and looked up under
 * another, and nothing generic could resolve a stored favourite back to the thing that plays it.
 *
 * The value that actually resolves is the network source's own id - S1687 put it on the hand-off precisely
 * so a player could read a file over the protocol it came from. The category is kept only as the fallback
 * for a network file whose source id was never recorded, so no favourite becomes unaddressable.
 */
fun favoriteSourceId(isNetworkSource: Boolean, networkSourceId: String?): String = when {
    !isNetworkSource -> SOURCE_ID_LOCAL
    else -> networkSourceId?.takeIf { it.isNotBlank() } ?: SOURCE_ID_NETWORK
}

/** A file the watch itself holds. */
const val SOURCE_ID_LOCAL = "local"

/** A network file whose source id was not recorded - the pre-S1846 spelling, kept so old marks resolve. */
const val SOURCE_ID_NETWORK = "network"

/** A direct stream keyed by its normalized address instead of a volatile catalog row id. */
const val SOURCE_ID_STREAM = "stream"

/** A stream is presentation-distinct but shares the existing favourite identity and storage. */
const val FAVORITE_ITEM_KIND_STREAM = "stream"

/**
 * Mirrors the phone stream-key rule because a channel address can survive catalog re-import while its row id cannot.
 */
fun normalizeWearStreamUrl(url: String): String {
    val trimmed = url.trim()
    val parsed = runCatching { URI(trimmed) }.getOrNull()
    val scheme = parsed?.scheme
    val host = parsed?.host
    if (parsed == null || scheme == null || host == null) {
        return trimmed.removeSuffix("/")
    }
    val lowerScheme = scheme.lowercase(Locale.ROOT)
    val userInfo = parsed.rawUserInfo?.let { "$it@" }.orEmpty()
    val port = parsed.port
    val portPart = if (port < 0 || port == defaultPortOf(lowerScheme)) "" else ":$port"
    val path = parsed.rawPath.orEmpty().removeSuffix("/")
    val query = parsed.rawQuery?.let { "?$it" }.orEmpty()
    val fragment = parsed.rawFragment?.let { "#$it" }.orEmpty()
    return lowerScheme + "://" + userInfo + host.lowercase(Locale.ROOT) + portPart + path + query + fragment
}

private fun defaultPortOf(scheme: String): Int = when (scheme) {
    "http" -> HTTP_DEFAULT_PORT
    "https" -> HTTPS_DEFAULT_PORT
    "rtsp" -> RTSP_DEFAULT_PORT
    else -> NO_DEFAULT_PORT
}

private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443
private const val RTSP_DEFAULT_PORT = 554
private const val NO_DEFAULT_PORT = -1

/**
 * S1846: the whole favourites list, from the two shapes the store holds.
 *
 * Pure on purpose. The store itself sits behind `EncryptedSharedPreferences`, which needs a real Android
 * runtime and cannot be exercised in a unit test - but the part that can actually be got wrong is here:
 * which entries survive, in what order, and which duplicate is dropped. Keeping it out of the store is what
 * makes that provable without a device.
 *
 * [records] win over [legacyKeys] describing the same file, because a record carries the name and kind the
 * legacy key never had. A legacy key that no record covers is still a favourite the user made and is listed.
 */
fun mergeFavorites(records: List<WearFavoriteRecord>, legacyKeys: Set<String>): List<WearFavoriteRecord> {
    val known = records.mapTo(mutableSetOf()) { it.identity }
    val legacy = legacyKeys
        .mapNotNull { WearFavoriteRecord.fromLegacyKey(it) }
        .filterNot { it.identity in known }
        .sortedBy { it.displayName }
    return records + legacy
}
