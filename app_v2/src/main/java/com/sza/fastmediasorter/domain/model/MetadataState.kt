package com.sza.fastmediasorter.domain.model

/**
 * Metadata enrichment lifecycle state of a [MediaFile].
 *
 * Introduced by S0237 — first-time SMB scan splits listing from metadata extraction,
 * so a `MediaFile` may temporarily exist with only the data the directory listing
 * provided (name, size, type, mimeType) and no extractor-derived fields yet.
 *
 * The state captures *what the consumer can trust on this instance*, not *what the
 * underlying file contains*. A file may be `PARTIAL` simply because the per-file
 * metadata budget elapsed during this scan; a later session may yield `COMPLETE` for
 * the same physical file.
 *
 * Cache discipline (see strategic spec §6.4): only `COMPLETE` instances are persisted
 * to [com.sza.fastmediasorter.core.cache.MediaFilesCacheManager]. `PENDING` and
 * `PARTIAL` live in-memory until they upgrade.
 */
enum class MetadataState {
    /**
     * Listing-only row — directory listing returned name / size / mimeType / lastModified
     * basics, but the per-file metadata extractor has not run yet (or has not started).
     *
     * Guaranteed populated: filename, full path, size, basic [MediaType], mimeType (when
     * derivable from name), `lastModified` if the listing carried it.
     *
     * May be null / default: `durationMs`, image/video resolution fields, EXIF date,
     * audio tags, embedded thumbnail hints.
     *
     * UI rendering convention: pending rows render the listing-derived fields and leave
     * extractor-derived fields blank (no placeholder dash, no spinner — see §6.3).
     */
    PENDING,

    /**
     * Metadata extraction attempted and elapsed the per-file timeout
     * ([com.sza.fastmediasorter.data.network.config.MetadataBudgetConfig.perFileTimeoutMs]),
     * so the result is whatever the extractor managed to read before the budget cut it
     * off — possibly a header fragment, possibly nothing.
     *
     * Guaranteed populated: same as [PENDING] plus any fields the extractor was able to
     * fill before the timeout fired.
     *
     * May be null / default: any field the extractor did not reach in time. Consumers
     * must treat absent fields the same way they do for [PENDING].
     *
     * Not persisted to [com.sza.fastmediasorter.core.cache.MediaFilesCacheManager] —
     * the next scan of the same resource will retry the extraction.
     */
    PARTIAL,

    /**
     * Metadata extractor ran to completion within the per-file budget. Every field the
     * source format can yield has been populated (subject to format-level absence —
     * e.g. a JPEG without EXIF date will still be `COMPLETE` with `null` date).
     *
     * This is the only state safe to persist to
     * [com.sza.fastmediasorter.core.cache.MediaFilesCacheManager]. Legacy instances
     * (constructed before S0237) default to this state to preserve source compatibility.
     */
    COMPLETE,
}
