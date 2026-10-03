package com.sza.fastmediasorter.data.common

import java.util.Locale

/** Canonical primary categories of the MEDIA-CLASSIFICATION contract, rule 1. */
enum class MediaCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    BOOK,
    STREAM,
    CONTAINER,
    DOCUMENT,
    UNKNOWN,
}

/**
 * Reference classifier of the MEDIA-CLASSIFICATION contract (0.10), owned by this product.
 *
 * The tables are classification-only: what the gallery lists and opens is [MediaTypeUtils], which
 * keeps to the decoders the app ships, so `.tiff` is IMAGE here and still absent from the gallery.
 */
object MediaCategoryClassifier {

    private val IMAGE = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif", "svg", "tiff", "ico", "wmf", "emf",
    )
    private val VIDEO = setOf(
        "mp4", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v", "avi", "mpg", "mpeg", "ts", "m2ts", "vob",
        "ogv", "divx", "m2v", "mts", "3g2", "asf",
    )

    // m4b defaults to AUDIO (rule 2): chapter cues that would make it BOOK are not in the name.
    private val AUDIO = setOf(
        "mp3", "m4a", "flac", "aac", "ogg", "wma", "opus", "amr", "awb", "ac3", "ec3", "ac4", "adts", "thd",
        "mka", "oga", "caf", "alac", "mia", "mid", "midi", "wav", "wave", "m4b",
    )
    private val BOOK = setOf("epub", "pdf", "cbz", "cbr", "fb2", "mobi")
    private val STREAM = setOf("m3u", "m3u8", "pls", "xspf", "fmsbcast")
    private val CONTAINER = setOf(
        "fd-sec", "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "cab", "arj", "lzh", "iso", "dmg", "img", "vhd",
        "vdi", "qcow2", "vmdk",
    )
    private val DOCUMENT = setOf(
        "doc", "docx", "rtf", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "txt", "md", "json", "xml",
        "csv", "tsv", "yaml", "yml", "toml", "ini", "conf",
    )

    private val CATEGORY_BY_EXTENSION: Map<String, MediaCategory> = buildMap {
        IMAGE.forEach { put(it, MediaCategory.IMAGE) }
        VIDEO.forEach { put(it, MediaCategory.VIDEO) }
        AUDIO.forEach { put(it, MediaCategory.AUDIO) }
        BOOK.forEach { put(it, MediaCategory.BOOK) }
        STREAM.forEach { put(it, MediaCategory.STREAM) }
        CONTAINER.forEach { put(it, MediaCategory.CONTAINER) }
        DOCUMENT.forEach { put(it, MediaCategory.DOCUMENT) }
    }

    private val TEMPORARY_SUFFIXES = listOf(".temp_copy", ".download")
    private val JUNK_NAMES = setOf("thumbs.db", "desktop.ini", ".ds_store")
    private val JUNK_EXTENSIONS = setOf("tmp", "swp")
    private const val GENERIC_MIME = "application/octet-stream"

    private val STREAM_MIMES = setOf(
        "audio/x-mpegurl",
        "audio/mpegurl",
        "application/x-mpegurl",
        "application/vnd.apple.mpegurl",
        "audio/x-scpls",
        "application/xspf+xml",
    )
    private val BOOK_MIMES = setOf(
        "application/pdf",
        "application/epub+zip",
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/vnd.comicbook-rar",
        "application/x-cbr",
        "application/x-fictionbook+xml",
        "application/x-mobipocket-ebook",
    )
    private val CONTAINER_MIMES = setOf(
        "application/zip", "application/vnd.rar", "application/x-rar-compressed", "application/x-7z-compressed",
        "application/x-tar", "application/gzip", "application/x-bzip2", "application/x-xz",
        "application/vnd.ms-cab-compressed", "application/x-iso9660-image", "application/x-apple-diskimage",
    )
    private val DOCUMENT_MIMES = setOf(
        "text/plain", "text/markdown", "text/csv", "text/tab-separated-values", "text/xml", "application/xml",
        "application/json", "application/yaml", "application/toml", "text/rtf", "application/rtf",
        "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
    )

    /** Every extension the contract table lists, lowercase, for conformance checks. */
    val canonicalExtensions: Map<String, MediaCategory> get() = CATEGORY_BY_EXTENSION

    /** Rule 3: lowercase, trailing dots and spaces and temporary download suffixes stripped. */
    fun normalizeName(fileName: String): String {
        var name = fileName.trimEnd('.', ' ').lowercase(Locale.ROOT)
        var stripped = true
        while (stripped) {
            stripped = false
            TEMPORARY_SUFFIXES.firstOrNull { name.endsWith(it) && name.length > it.length }?.let { suffix ->
                name = name.removeSuffix(suffix).trimEnd('.', ' ')
                stripped = true
            }
        }
        return name
    }

    /**
     * Rule 5: system junk excluded from browsing before classification.
     *
     * [includeDotFiles] = false leaves other dot-files to the caller, because product browsing
     * already hides them behind the per-resource "show hidden files" toggle and must honour it.
     */
    fun isSystemJunk(fileName: String, includeDotFiles: Boolean = true): Boolean {
        val name = fileName.substringAfterLast('/').trimEnd('.', ' ').lowercase(Locale.ROOT)
        val extension = name.substringAfterLast('.', "")
        return name in JUNK_NAMES ||
            extension in JUNK_EXTENSIONS ||
            extension.startsWith('~') ||
            (includeDotFiles && name.startsWith('.'))
    }

    /** Category by name only; junk resolves to null because it receives no category (rule 5). */
    fun classify(fileName: String): MediaCategory? {
        if (isSystemJunk(fileName)) return null
        val extension = normalizeName(fileName.substringAfterLast('/')).substringAfterLast('.', "")
        return CATEGORY_BY_EXTENSION[extension] ?: MediaCategory.UNKNOWN
    }

    /** Rule 6: a valid, non-generic MIME decides; a null, generic or unmapped one falls back to the name. */
    fun classify(mimeType: String?, fileName: String): MediaCategory? {
        if (isSystemJunk(fileName)) return null
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val byMime = mime?.takeIf { it.isNotEmpty() && it != GENERIC_MIME }?.let(::categoryForMime)
        return byMime ?: classify(fileName)
    }

    private fun categoryForMime(mime: String): MediaCategory? = when {
        mime in STREAM_MIMES -> MediaCategory.STREAM
        mime in BOOK_MIMES -> MediaCategory.BOOK
        mime in CONTAINER_MIMES -> MediaCategory.CONTAINER
        mime.startsWith("image/") -> MediaCategory.IMAGE
        mime.startsWith("video/") -> MediaCategory.VIDEO
        mime.startsWith("audio/") -> MediaCategory.AUDIO
        mime in DOCUMENT_MIMES -> MediaCategory.DOCUMENT
        else -> null
    }
}
