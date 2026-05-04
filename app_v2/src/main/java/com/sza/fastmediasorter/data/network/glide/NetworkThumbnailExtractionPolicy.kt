package com.sza.fastmediasorter.data.network.glide

/**
 * Policy: which video container formats must skip MediaMetadataRetriever frame extraction
 * on network streams (SMB/SFTP/FTP).
 *
 * Single point of truth — extend [BLOCKED_EXTENSIONS] as new failure patterns are confirmed
 * empirically from session logs. S0063.
 *
 * Rationale: for blocked formats the system extractor fails almost immediately with a status
 * error, but the 10-second watchdog still fires before the failure is reported — wasting a
 * full timeout slot per file and flooding the log with predictable "TIMEOUT after 10000ms"
 * and "getFrameAtTime returned null" lines.
 *
 * Local files (file://, bare paths) are never blocked: the extractor works reliably there.
 *
 * BDMV/DVD (S0077): neither NetworkFileModelLoader nor NetworkVideoFrameDecoder handles
 * vob/m2ts/mts/m2t/ts/ifo/bup on network streams — both loaders reject these formats.
 * Blocking them here prevents NoModelLoaderAvailableException on every bind.
 * If a decoder for optical-disc containers is added in the future, remove the entries here.
 */
object NetworkThumbnailExtractionPolicy {

    /**
     * Lowercase file extensions that consistently fail MediaMetadataRetriever on network streams.
     *
     * AVI: [setDataSource] triggers status 0x80000000 almost immediately via NetworkMediaDataSource,
     *      then the 10-second watchdog fires — every AVI on SMB/SFTP/FTP wastes a full slot.
     *
     * Add further extensions here (e.g. "flv", "wmv") as confirmed by session log analysis.
     */
    val BLOCKED_EXTENSIONS: Set<String> = setOf(
        // AVI: setDataSource triggers status 0x80000000 almost immediately via NetworkMediaDataSource;
        //      the 10-second watchdog fires after every attempt. S0063.
        "avi",
        // BDMV / DVD optical-disc containers: neither NetworkFileModelLoader nor NetworkVideoFrameDecoder
        // handles these formats — both loaders reject them, causing NoModelLoaderAvailableException on
        // every bind. Block them here so the fast-path placeholder fires before Glide is invoked. S0077.
        "vob", "m2ts", "mts", "m2t", "ts", "ifo", "bup"
    )

    /**
     * Returns true when frame extraction should be skipped for the given [extension] on a
     * network stream. Input is compared case-insensitively.
     */
    fun shouldSkipNetworkExtraction(extension: String): Boolean =
        extension.lowercase() in BLOCKED_EXTENSIONS
}
