package com.sza.fastmediasorter.wear.domain.model

/**
 * S2130 raised this to 5 for [WearPhoneResourceResponseStatus.NO_RESOURCE_FOR_TYPE].
 *
 * Both sides move together, in one change: an unknown enum name deserialises to null through Gson,
 * so a watch built before the value would read the new status as a malformed page rather than as an
 * unknown one. There is no installed base to negotiate with - the pair ships as one artifact set.
 */
const val WEAR_PHONE_RESOURCE_SCHEMA_VERSION = 5

enum class WearPhoneResourceRequestKind {
    ROOT,
    CHILDREN,
    OPEN,

    /**
     * S2129: one picture for the item named by `itemToken`.
     *
     * The answer is an ordinary page carrying that single item with its `thumbnailBase64` filled, so
     * this kind needs no response type and no transport of its own. Pictures left the page response
     * because a page-wide budget spent itself on the first few rows and the rest arrived blank.
     */
    THUMBNAIL
}

enum class WearPhoneResourceResponseStatus {
    OK,
    EMPTY,

    /**
     * S2130: the phone has resources to show, and not one of them is configured to hold the kind
     * this screen asked for.
     *
     * Kept apart from [EMPTY] because the watch can say something useful about it and nothing useful
     * about the other: EMPTY means a reachable place currently holds no files, this means no such
     * place exists for this category. The watch cannot work this out for itself - it never receives
     * the resource list or the per-resource type configuration the phone filters on.
     */
    NO_RESOURCE_FOR_TYPE,
    PHONE_UNAVAILABLE,

    /**
     * S1697: the phone replied, but the resource behind it did not - an unreachable SMB or SFTP
     * stand, not a lost watch link. Kept apart from [PHONE_UNAVAILABLE] so the screen can name the
     * thing that is actually down.
     */
    SOURCE_UNAVAILABLE,
    ACCESS_DENIED,
    UNSUPPORTED_MEDIA,
    TRANSFER_REJECTED,
    NOT_FOUND
}

data class WearPhoneResourceRequest(
    val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    val requestId: String,
    val kind: WearPhoneResourceRequestKind,
    val parentToken: String? = null,
    val pageToken: String? = null,
    val itemToken: String? = null,
    /**
     * S1846: which kind of file the watch is asking for, or null for "everything".
     *
     * The accepted values are the watch route's own vocabulary, so the two sides cannot drift apart on
     * spelling: `photos`, `videos`, `music`, `documents`, `all`. `all` and null mean the same thing and
     * both leave the phone's answer unnarrowed; the unfiltered `Phone` entrance sends null.
     */
    val mediaType: String? = null,
    val isFlat: Boolean? = null
)

data class WearPhoneResourceItem(
    val token: String,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val isDirectory: Boolean,
    /**
     * Base64 of a small image the phone prepared, or null when it prepared none.
     *
     * Nullable rather than an empty default: a missing key and an empty picture would otherwise
     * read the same, and only one of the two means "show the type icon".
     */
    val thumbnailBase64: String? = null
)

data class WearPhoneResourcePage(
    val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    val requestId: String,
    val status: WearPhoneResourceResponseStatus,
    val items: List<WearPhoneResourceItem> = emptyList(),
    val nextPageToken: String? = null
)

/**
 * S2476: returns the file name without extension for compact display on watch screens.
 */
val WearPhoneResourceItem.displayName: String
    get() {
        if (isDirectory) return name
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }
