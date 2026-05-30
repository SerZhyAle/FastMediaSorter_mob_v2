package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.util.BinaryFileTypeDetector

object MediaExtensions {
    val IMAGE = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "avif")
    val VIDEO = setOf(
        "mp4", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpg", "mpeg",
        "ts", "m2ts", "vob", "ogv", "divx", "m2v", "mts"
    )
    val AUDIO = setOf(
        "mp3", "flac", "aac", "ogg", "m4a", "wma", "opus", 
        "amr", "awb", "ac3", "ec3", "ac4", "adts", "thd", "mka", "oga", "caf", "alac", "mia"
    ) + MidiPlaybackPolicy.SUPPORTED_EXTENSIONS
    val TEXT = setOf("txt", "md", "log", "json", "xml", "csv", "conf", "ini", "properties", "yml", "yaml")
    val PDF = setOf("pdf")
    val EPUB = setOf("epub")
    val OFFICE_DOCUMENT = setOf("doc", "docx", "rtf", "odt")
    
    fun isImage(extension: String): Boolean = extension.lowercase() in IMAGE
    fun isVideo(extension: String): Boolean = extension.lowercase() in VIDEO
    fun isAudio(extension: String): Boolean = extension.lowercase() in AUDIO
    fun isText(extension: String): Boolean = extension.lowercase() in TEXT
    fun isPdf(extension: String): Boolean = extension.lowercase() in PDF
    fun isEpub(extension: String): Boolean = extension.lowercase() in EPUB
    fun isOfficeDocument(extension: String): Boolean = extension.lowercase() in OFFICE_DOCUMENT
    
    fun getMediaType(extension: String): MediaType {
        val lowerExt = extension.lowercase()
        
        // Check binary types first (Task 6)
        if (BinaryFileTypeDetector.isBinaryExtension(lowerExt)) {
            return BinaryFileTypeDetector.detectType(lowerExt)
        }
        
        return when {
            lowerExt in IMAGE -> MediaType.IMAGE
            lowerExt in VIDEO -> MediaType.VIDEO
            lowerExt in AUDIO -> MediaType.AUDIO
            lowerExt in TEXT -> MediaType.TEXT
            lowerExt in PDF -> MediaType.PDF
            lowerExt in EPUB -> MediaType.EPUB
            lowerExt in OFFICE_DOCUMENT -> MediaType.OFFICE_DOCUMENT
            else -> MediaType.IMAGE // Default fallback
        }
    }
}
