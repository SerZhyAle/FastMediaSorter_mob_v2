package com.sza.fastmediasorter.wear.domain.model

/**
 * What kind of thing a cell stands for, independent of how it is drawn.
 *
 * The glyph and the semantic tone for each entry live in `ui/common/ContentTypeCatalog`, not here:
 * the module keeps drawable ids and Compose types out of the domain layer, which is the same reason
 * the home screen holds its own section icons rather than hanging them on the section model.
 *
 * S2003: before this enum the same handful of types was described on three screens in three
 * unrelated vocabularies - emoji, generic platform vectors, and no icon at all - with no single
 * point of truth to correct.
 */
enum class WearContentType {
    MUSIC,
    VIDEO,
    IMAGE,
    DOCUMENT,
    FOLDER,
    STREAM,
    OTHER
}

private const val IMAGE_MIME_PREFIX = "image/"
private const val VIDEO_MIME_PREFIX = "video/"
private const val AUDIO_MIME_PREFIX = "audio/"

/** Any `text/` subtype is a document: plain text, markdown, csv and html all read as one. */
private const val TEXT_MIME_PREFIX = "text/"

/**
 * S2691: the non-text document mime types this module recognises.
 *
 * Pinned rather than taken as "every `application/` type", because an archive or an installer on a
 * share carries that prefix too and the phone side keeps those apart from documents as its
 * `BINARY_*` group (S2640). `MediaMimeTypes` pins pdf and epub itself and takes the office formats
 * from the platform table, so both spellings a listing can produce are covered.
 */
private val DOCUMENT_MIME_TYPES: Set<String> = setOf(
    "application/pdf",
    "application/epub+zip",
    "application/rtf",
    "application/msword",
    "application/vnd.ms-excel",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.oasis.opendocument.spreadsheet",
    "application/vnd.oasis.opendocument.presentation"
)

/**
 * The content type a mime type names, or null when it names none.
 *
 * A miss answers null rather than [WearContentType.OTHER] because the caller falls back to the
 * screen's own media type: OTHER would claim the file is unclassifiable while the screen still
 * knows exactly what kind of library it is listing.
 *
 * The prefixes keep their trailing slash - a bare "image" also matches a type that merely starts
 * with those letters.
 *
 * S2691: documents classify here too. This is the module's one mime resolver, so leaving them
 * unclassified made every caller invent its own answer for a pdf - the badge drew the route's media
 * type, the refine filter placed it under a category it does not belong to, and the network listing
 * had nothing to filter a Documents category by at all.
 */
fun contentTypeForMime(mimeType: String?): WearContentType? {
    if (mimeType == null) {
        return null
    }
    val normalized = mimeType.lowercase()
    return when {
        normalized.startsWith(IMAGE_MIME_PREFIX) -> WearContentType.IMAGE
        normalized.startsWith(VIDEO_MIME_PREFIX) -> WearContentType.VIDEO
        normalized.startsWith(AUDIO_MIME_PREFIX) -> WearContentType.MUSIC
        normalized.startsWith(TEXT_MIME_PREFIX) -> WearContentType.DOCUMENT
        normalized in DOCUMENT_MIME_TYPES -> WearContentType.DOCUMENT
        else -> null
    }
}

/**
 * The content type a list entry stands for, judged from what the entry itself carries.
 *
 * S2129: for the phone-file lists, which hold a mime type and a directory flag and nothing else.
 * Unlike [contentTypeForMime] this always answers, because the caller is a drawing point that has
 * to put some glyph in the row - there is no screen-level media type to fall back to when a phone
 * folder mixes audio, images and documents in one listing.
 *
 * A directory wins over its mime type: some providers hand a folder a mime of its own, and a
 * folder that drew as an audio file would misreport what tapping it does.
 */
fun contentTypeForEntry(mimeType: String?, isDirectory: Boolean): WearContentType = when {
    isDirectory -> WearContentType.FOLDER
    else -> contentTypeForMime(mimeType) ?: WearContentType.DOCUMENT
}

/** What a browse surface stands for when the file's own mime type classifies nothing. */
fun MediaType.asContentType(): WearContentType = when (this) {
    MediaType.MUSIC -> WearContentType.MUSIC
    MediaType.VIDEO -> WearContentType.VIDEO
    MediaType.PHOTO -> WearContentType.IMAGE
}
