package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2130 raised this to 5 for [WearPhoneResourceResponseStatus.NO_RESOURCE_FOR_TYPE].
 *
 * Both sides move together, in one change: an unknown enum name deserialises to null through Gson,
 * so a watch built before the value would read the new status as a malformed page rather than as an
 * unknown one. There is no installed base to negotiate with - the pair ships as one artifact set.
 */
const val WEAR_PHONE_RESOURCE_SCHEMA_VERSION = 5

enum class WearPhoneResourceRequestKind {
    @SerializedName("ROOT")
    ROOT,

    @SerializedName("CHILDREN")
    CHILDREN,

    @SerializedName("OPEN")
    OPEN,

    /**
     * S2129: one picture for the item named by `itemToken`.
     *
     * The answer is an ordinary page carrying that single item with its `thumbnailBase64` filled, so
     * this kind needs no response type and no transport of its own. Pictures left the page response
     * because a page-wide budget spent itself on the first few rows and the rest arrived blank.
     */
    @SerializedName("THUMBNAIL")
    THUMBNAIL
}

enum class WearPhoneResourceResponseStatus {
    @SerializedName("OK")
    OK,

    @SerializedName("EMPTY")
    EMPTY,

    /**
     * S2130: the phone has resources to show, and not one of them is configured to hold the kind the
     * watch asked for.
     *
     * Distinct from [EMPTY] because the two are different facts about different things, and only one
     * of them is actionable: EMPTY says a place the watch can reach currently has no files, this says
     * no such place exists for this category at all. The owner reported the symptom as "Video opens
     * empty although the phone has videos" - true on both counts, because the scan never visits a
     * resource whose configured types exclude video (strategic ADR-5). The watch cannot re-derive
     * this: it never sees the resource list or its per-resource type configuration.
     */
    @SerializedName("NO_RESOURCE_FOR_TYPE")
    NO_RESOURCE_FOR_TYPE,

    @SerializedName("PHONE_UNAVAILABLE")
    PHONE_UNAVAILABLE,

    /**
     * S1697: the phone answered, but the resource's own backing store did not. Distinct from
     * [PHONE_UNAVAILABLE] because the two send the user after different things - a dead NAS is not
     * a dead watch link, and telling someone to reconnect a phone that is replying wastes the trip.
     */
    @SerializedName("SOURCE_UNAVAILABLE")
    SOURCE_UNAVAILABLE,

    @SerializedName("ACCESS_DENIED")
    ACCESS_DENIED,

    @SerializedName("UNSUPPORTED_MEDIA")
    UNSUPPORTED_MEDIA,

    @SerializedName("TRANSFER_REJECTED")
    TRANSFER_REJECTED,

    @SerializedName("NOT_FOUND")
    NOT_FOUND
}

data class WearPhoneResourceRequest(
    @SerializedName("schemaVersion") val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    @SerializedName("requestId") val requestId: String,
    @SerializedName("kind") val kind: WearPhoneResourceRequestKind,
    @SerializedName("parentToken") val parentToken: String? = null,
    @SerializedName("pageToken") val pageToken: String? = null,
    @SerializedName("itemToken") val itemToken: String? = null,
    /**
     * S1846: which kind of file the watch is asking for, or null for "everything".
     *
     * The accepted values are the watch route's own vocabulary, so the two sides cannot drift apart on
     * spelling: `photos`, `videos`, `music`, `documents`, `all`. `all` and null mean the same thing and
     * both leave the phone's answer unnarrowed; the unfiltered `Phone` entrance sends null.
     */
    @SerializedName("mediaType") val mediaType: String? = null,
    @SerializedName("isFlat") val isFlat: Boolean? = null
)

data class WearPhoneResourceItem(
    @SerializedName("token") val token: String,
    @SerializedName("name") val name: String,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("sizeBytes") val sizeBytes: Long? = null,
    @SerializedName("isDirectory") val isDirectory: Boolean,
    /**
     * Base64 of a small image, or null when this item carries none.
     *
     * Nullable rather than an empty default: Gson turns a missing key into the default, so an
     * empty string could not be told apart from "the phone sent nothing".
     */
    @SerializedName("thumbnailBase64") val thumbnailBase64: String? = null
)

data class WearPhoneResourcePage(
    @SerializedName("schemaVersion") val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    @SerializedName("requestId") val requestId: String,
    @SerializedName("status") val status: WearPhoneResourceResponseStatus,
    @SerializedName("items") val items: List<WearPhoneResourceItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)
