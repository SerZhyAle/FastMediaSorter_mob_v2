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
        return when {
            mimeType == "image/gif" -> MediaType.GIF
            mimeType.startsWith("image/") -> MediaType.IMAGE
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("audio/") -> MediaType.AUDIO
            mimeType == "text/plain" || mimeType == "application/json" || mimeType == "text/xml" -> MediaType.TEXT
            mimeType == "application/pdf" -> MediaType.PDF
            mimeType == "application/epub+zip" -> MediaType.EPUB
            else -> null
        }
    }

    fun isFileSizeInRange(size: Long, mediaType: MediaType, filter: SizeFilter): Boolean {
        return when (mediaType) {
            MediaType.IMAGE, MediaType.GIF -> size in filter.imageSizeMin..filter.imageSizeMax
            MediaType.VIDEO -> size in filter.videoSizeMin..filter.videoSizeMax
            MediaType.AUDIO -> size in filter.audioSizeMin..filter.audioSizeMax
            MediaType.TEXT -> true // No size filtering for now
            MediaType.PDF -> true // No size filtering for now
            MediaType.EPUB -> true // No size filtering for now
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
                MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> {
                    // Binary files don't have predefined extension sets
                }
            }
        }
        return extensions
    }
}
