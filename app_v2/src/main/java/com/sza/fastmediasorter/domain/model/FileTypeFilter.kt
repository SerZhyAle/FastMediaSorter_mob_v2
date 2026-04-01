package com.sza.fastmediasorter.domain.model

/**
 * Bitmask flags for scheduled operation file type filter.
 * Stored in DB as INTEGER column `file_type_mask`.
 *
 * ALL_FILES (bit 0) — special flag: process ALL files in the source resource,
 *   including non-media files (binaries, archives, unknown formats).
 *   Activated by passing MediaType.entries.toSet() to the scanner (triggers
 *   isAllFilesMode / null-extensions path in SmbMediaScanner, CloudMediaScanner, etc.).
 *   Subdirectory recursion is governed by resource.scanSubdirectories as usual.
 *   When ALL_FILES is set, the other flags are irrelevant during execution.
 *
 * IMAGES    (bit 1) — IMAGE + GIF
 * AUDIO     (bit 2) — AUDIO
 * VIDEO     (bit 3) — VIDEO
 * DOCUMENTS (bit 4) — PDF + EPUB + TEXT
 */
object FileTypeFlags {
    const val ALL_FILES: Int = 1 shl 0   // 1
    const val IMAGES:    Int = 1 shl 1   // 2
    const val AUDIO:     Int = 1 shl 2   // 4
    const val VIDEO:     Int = 1 shl 3   // 8
    const val DOCUMENTS: Int = 1 shl 4   // 16

    /** All media flags combined (without ALL_FILES). */
    const val ALL_MEDIA: Int = IMAGES or AUDIO or VIDEO or DOCUMENTS  // 30

    /** Default mask for new operations: ALL_FILES | ALL_MEDIA = 31. */
    const val DEFAULT: Int = ALL_FILES or ALL_MEDIA  // 31

    fun isAllFiles(mask: Int): Boolean    = (mask and ALL_FILES)  != 0
    fun hasImages(mask: Int): Boolean     = (mask and IMAGES)     != 0
    fun hasAudio(mask: Int): Boolean      = (mask and AUDIO)      != 0
    fun hasVideo(mask: Int): Boolean      = (mask and VIDEO)      != 0
    fun hasDocuments(mask: Int): Boolean  = (mask and DOCUMENTS)  != 0

    /**
     * Convert legacy FileTypeFilter enum name (from old XML backups / DB rows before migration)
     * to the corresponding bitmask value.
     */
    fun fromLegacyName(name: String): Int = when (name.uppercase()) {
        // Legacy enum ALL meant "all media", not new ALL_FILES.
        "ALL"       -> ALL_MEDIA
        "IMAGES"    -> IMAGES
        "AUDIO"     -> AUDIO
        "VIDEO"     -> VIDEO
        "DOCUMENTS" -> DOCUMENTS
        else        -> ALL_MEDIA
    }
}
