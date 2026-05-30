package com.sza.fastmediasorter.data.common

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.util.BinaryFileTypeDetector
import java.util.Locale

object MediaTypeUtils {
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "avif")
    val GIF_EXTENSIONS = setOf("gif")
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v", "avi", "mpg", "mpeg",
        "ts", "m2ts", "vob", "ogv", "divx", "m2v", "mts"
    )
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "flac", "aac", "ogg", "wma", "opus",
        "amr", "awb", "ac3", "ec3", "ac4", "adts", "thd", "mka", "oga", "caf", "alac", "mia", "mid", "midi"
    )
    val TEXT_EXTENSIONS = setOf(
        // Plain text and documentation
        "txt", "md", "markdown", "rst", "log", "readme", "license", "changelog", "authors",
        // Data formats (text-based only)
        "json", "xml", "csv", "tsv", "yaml", "yml", "toml", "ini", "conf", "config", "properties",
        // Code files - Web
        "html", "htm", "css", "scss", "sass", "less", "js", "jsx", "ts", "tsx", "vue", "svelte",
        // Code files - Backend
        "java", "kt", "kts", "py", "rb", "php", "go", "rs", "c", "cpp", "h", "hpp", "cs", "swift",
        // Code files - Scripts
        "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd", "vbs", "lua", "pl", "r",
        // Code files - Other
        "sql", "gradle", "groovy", "scala", "clj", "ex", "exs", "erl", "hrl", "dart", "m", "mm",
        // Markup and templates
        "tex", "latex", "adoc", "asciidoc", "org", "textile", "wiki", "mediawiki",
        // Config and build files
        "gitignore", "gitattributes", "editorconfig", "dockerignore", "npmrc", "yarnrc",
        "gradle", "gradlew", "maven", "pom", "build", "makefile", "cmake", "ninja",
        // Schema and IDL (text-based)
        "proto", "thrift",
        // Other text formats
        "diff", "patch", "ics", "vcf", "vcard", "m3u", "m3u8", "pls", "asc",
        "env", "envrc", "htaccess", "htpasswd", "robots", "sitemap", "rss", "atom", "opml"
    )
    val PDF_EXTENSIONS = setOf("pdf")
    val EPUB_EXTENSIONS = setOf("epub")
    private val OFFICE_DOCUMENT_MIME_TYPE_FAMILIES = mapOf(
        "application/msword" to OfficeDocumentFamily.WORD,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to OfficeDocumentFamily.WORD,
        "application/rtf" to OfficeDocumentFamily.WORD,
        "application/x-rtf" to OfficeDocumentFamily.WORD,
        "text/rtf" to OfficeDocumentFamily.WORD,
        "application/vnd.oasis.opendocument.text" to OfficeDocumentFamily.WORD,
        "application/vnd.ms-excel" to OfficeDocumentFamily.SPREADSHEET,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to OfficeDocumentFamily.SPREADSHEET,
        "application/vnd.oasis.opendocument.spreadsheet" to OfficeDocumentFamily.SPREADSHEET,
        "application/vnd.ms-powerpoint" to OfficeDocumentFamily.PRESENTATION,
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to OfficeDocumentFamily.PRESENTATION,
        "application/vnd.oasis.opendocument.presentation" to OfficeDocumentFamily.PRESENTATION,
    )

    private val OFFICE_DOCUMENT_DEFAULT_MIME_BY_EXTENSION = mapOf(
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "rtf" to "application/rtf",
        "odt" to "application/vnd.oasis.opendocument.text",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odp" to "application/vnd.oasis.opendocument.presentation",
    )

    val OFFICE_DOCUMENT_EXTENSIONS: Set<String>
        get() = OfficeDocumentFamilyCatalog.extensionToFamily.keys

    val OFFICE_DOCUMENT_MIME_TYPES: Set<String>
        get() = OFFICE_DOCUMENT_MIME_TYPE_FAMILIES
            .filterValues { it in OfficeDocumentFamilyCatalog.supportedFamilies }
            .keys

    fun getMediaType(fileName: String): MediaType? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            IMAGE_EXTENSIONS.contains(extension) -> MediaType.IMAGE
            GIF_EXTENSIONS.contains(extension) -> MediaType.GIF
            VIDEO_EXTENSIONS.contains(extension) -> MediaType.VIDEO
            AUDIO_EXTENSIONS.contains(extension) -> MediaType.AUDIO
            TEXT_EXTENSIONS.contains(extension) -> MediaType.TEXT
            PDF_EXTENSIONS.contains(extension) -> MediaType.PDF
            EPUB_EXTENSIONS.contains(extension) -> MediaType.EPUB
            OFFICE_DOCUMENT_EXTENSIONS.contains(extension) -> MediaType.OFFICE_DOCUMENT
            // Task 6: Check for binary file types
            BinaryFileTypeDetector.isBinaryExtension(extension) -> BinaryFileTypeDetector.detectType(extension)
            else -> null
        }
    }
    
    /**
     * Get media type for All Files mode (includes binary files)
     * @param fileName File name to check
     * @param isAllFilesMode If true, unknown files are treated as binary or TEXT
     * @return MediaType or null if not recognized
     */
    fun getMediaTypeForAllFiles(fileName: String, isAllFilesMode: Boolean): MediaType? {
        val type = getMediaType(fileName)
        if (type != null) return type
        
        // In All Files mode, treat unknown files as TEXT fallback
        return if (isAllFilesMode) MediaType.TEXT else null
    }

    fun getMediaTypeFromMime(mimeType: String?): MediaType? {
        if (mimeType == null) return null
        val normalizedMimeType = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return when {
            normalizedMimeType == "image/gif" -> MediaType.GIF
            normalizedMimeType.startsWith("image/") -> MediaType.IMAGE
            normalizedMimeType.startsWith("video/") -> MediaType.VIDEO
            normalizedMimeType.startsWith("audio/") -> MediaType.AUDIO
            normalizedMimeType == "text/plain" || normalizedMimeType == "application/json" || normalizedMimeType == "text/xml" -> MediaType.TEXT
            normalizedMimeType == "application/pdf" -> MediaType.PDF
            normalizedMimeType == "application/epub+zip" -> MediaType.EPUB
            normalizedMimeType in OFFICE_DOCUMENT_MIME_TYPES -> MediaType.OFFICE_DOCUMENT
            else -> null
        }
    }

    /** MIME-first, extension fallback. Covers cases where SAF / cloud providers return null or non-standard MIME. */
    fun getMediaTypeFromMimeOrExtension(mimeType: String?, fileName: String): MediaType? =
        getMediaTypeFromMime(mimeType) ?: getMediaType(fileName)

    fun officeMimeTypeForFileName(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in OfficeDocumentFamilyCatalog.extensionToFamily) return null
        return OFFICE_DOCUMENT_DEFAULT_MIME_BY_EXTENSION[extension]
    }

    fun officeProbeForMimeType(mimeType: String): Pair<String, String>? {
        val normalizedMimeType = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (normalizedMimeType !in OFFICE_DOCUMENT_MIME_TYPES) return null
        val extension = OFFICE_DOCUMENT_DEFAULT_MIME_BY_EXTENSION.entries
            .firstOrNull { it.value == normalizedMimeType }
            ?.key
            ?: return null
        return extension to normalizedMimeType
    }

    fun isFileSizeInRange(size: Long, mediaType: MediaType, filter: SizeFilter): Boolean {
        return when (mediaType) {
            MediaType.IMAGE, MediaType.GIF -> size in filter.imageSizeMin..filter.imageSizeMax
            MediaType.VIDEO -> size in filter.videoSizeMin..filter.videoSizeMax
            MediaType.AUDIO -> size in filter.audioSizeMin..filter.audioSizeMax
            MediaType.TEXT -> true // No size filtering for now
            MediaType.PDF -> true // No size filtering for now
            MediaType.EPUB -> true // No size filtering for now
            MediaType.OFFICE_DOCUMENT -> true // External viewer route, no size filtering
            // Task 6: Binary files - no size filtering
            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, 
            MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> true
        }
    }

    fun buildExtensionsSet(supportedTypes: Set<MediaType>): Set<String> {
        val extensions = mutableSetOf<String>()
        supportedTypes.forEach { type ->
            when (type) {
                MediaType.IMAGE -> extensions.addAll(IMAGE_EXTENSIONS)
                MediaType.GIF -> extensions.addAll(GIF_EXTENSIONS)
                MediaType.VIDEO -> extensions.addAll(VIDEO_EXTENSIONS)
                MediaType.AUDIO -> extensions.addAll(AUDIO_EXTENSIONS)
                MediaType.TEXT -> extensions.addAll(TEXT_EXTENSIONS)
                MediaType.PDF -> extensions.addAll(PDF_EXTENSIONS)
                MediaType.EPUB -> extensions.addAll(EPUB_EXTENSIONS)
                MediaType.OFFICE_DOCUMENT -> extensions.addAll(OFFICE_DOCUMENT_EXTENSIONS)
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                    // Binary files don't have predefined extension sets
                }
            }
        }
        return extensions
    }
}
