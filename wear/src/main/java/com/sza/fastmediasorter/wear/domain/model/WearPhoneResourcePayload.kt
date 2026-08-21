package com.sza.fastmediasorter.wear.domain.model

const val WEAR_PHONE_RESOURCE_SCHEMA_VERSION = 3

enum class WearPhoneResourceRequestKind {
    ROOT,
    CHILDREN,
    OPEN
}

enum class WearPhoneResourceResponseStatus {
    OK,
    EMPTY,
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
    val mediaType: String? = null
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
