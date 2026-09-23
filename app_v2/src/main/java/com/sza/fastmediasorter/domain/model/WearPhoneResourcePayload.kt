package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2130 raised this to 5 for [WearPhoneResourceResponseStatus.NO_RESOURCE_FOR_TYPE], S2981 to 6 for
 * [WearPhoneResourceResponseStatus.COMPANION_DISABLED], S3359 to 7 for the delete request and its ack.
 *
 * Both sides move together, in one change: an unknown enum name deserialises to null through Gson,
 * so a watch built before the value would read the new status as a malformed page rather than as an
 * unknown one. There is no installed base to negotiate with - the pair ships as one artifact set.
 */
const val WEAR_PHONE_RESOURCE_SCHEMA_VERSION = 7

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
    NOT_FOUND,

    /**
     * S2981: the phone received the request, and its Wear Companion switch is off.
     *
     * Distinct from [PHONE_UNAVAILABLE] because the watch used to learn this only by timing out, and
     * then told the wearer to open the app and bring the phone closer - both already true. The fix
     * the wearer needs is one setting on the phone, so the refusal names it instead of staying silent.
     */
    @SerializedName("COMPANION_DISABLED")
    COMPANION_DISABLED
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
     * spelling: `photos`, `videos`, `music`, `documents`, `recents`, `all`. `all` and null mean the same
     * thing and both leave the phone's answer unnarrowed; the unfiltered `Phone` entrance sends null.
     *
     * S3160: anything outside that list is answered [WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA]
     * rather than served unnarrowed. A watch that introduces a token this phone build does not carry
     * gets a refusal it can word, instead of a list of every media kind that looks like the filtered one.
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
     * Nullable rather than an empty default: an empty string could not be told apart from "the phone
     * sent nothing". S2885 corrected the reason once given here - Gson does NOT turn a missing key
     * into the Kotlin default, it leaves a reference field null, which is why the nullability has to
     * be declared rather than assumed.
     */
    @SerializedName("thumbnailBase64") val thumbnailBase64: String? = null
)

data class WearPhoneResourcePage(
    @SerializedName("schemaVersion") val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    @SerializedName("requestId") val requestId: String,
    @SerializedName("status") val status: WearPhoneResourceResponseStatus,
    // S2885: nullable because Gson leaves an absent field null whatever the Kotlin default says. A
    // page that carries no `items` key is an empty page, so every reader goes through `.orEmpty()`.
    @SerializedName("items") val items: List<WearPhoneResourceItem>? = null,
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)

/**
 * S3359: what this phone did with the original behind a delete request.
 *
 * Only [DELETED] lets the watch report a move; every other member, and an answer that never arrives,
 * mean the copy is on the watch and the original is still here - which strategic §7 requires to be
 * said plainly rather than rounded up to "moved".
 */
enum class WearPhoneResourceDeleteOutcome {

    /** The original is gone from this phone. */
    @SerializedName("DELETED")
    DELETED,

    /**
     * This phone can reach the file and may not remove it without a system dialog.
     *
     * The request arrives in a listener service with no Activity behind it, so a MediaStore consent
     * prompt has nothing to show itself on. Distinct from [NOT_FOUND] because nothing is wrong: the
     * copy is stored, the original is where it was, and the owner can remove it here if they want to.
     */
    @SerializedName("COPIED_ONLY")
    COPIED_ONLY,

    /** The token resolved to nothing this phone serves - moved, renamed or on a withdrawn resource. */
    @SerializedName("NOT_FOUND")
    NOT_FOUND,

    /**
     * The file behind the token is no longer the size the watch copied.
     *
     * The token names a resource and a path and carries no identity of its own, so without this
     * refusal a file replaced between the copy and the request would be deleted in the original's
     * place (strategic §7, the token-after-rename risk).
     */
    @SerializedName("SIZE_MISMATCH")
    SIZE_MISMATCH,

    /** The owner switched the Wear Companion off here, so this phone acts on nothing the watch asks. */
    @SerializedName("COMPANION_DISABLED")
    COMPANION_DISABLED
}

/**
 * S3359: one ask to remove the original of a file the watch has already published.
 *
 * [expectedSizeBytes] is the length of the copy the watch verified, not the size the browse page
 * announced - the whole point of the field is that this phone re-measures what it is about to delete.
 */
data class WearPhoneResourceDeleteRequest(
    @SerializedName("schemaVersion") val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    @SerializedName("requestId") val requestId: String,
    @SerializedName("token") val token: String,
    @SerializedName("expectedSizeBytes") val expectedSizeBytes: Long
)

/** S3359: this phone's answer to one [WearPhoneResourceDeleteRequest], correlated by `requestId`. */
data class WearPhoneResourceDeleteAck(
    @SerializedName("schemaVersion") val schemaVersion: Int = WEAR_PHONE_RESOURCE_SCHEMA_VERSION,
    @SerializedName("requestId") val requestId: String,
    @SerializedName("outcome") val outcome: WearPhoneResourceDeleteOutcome
)
