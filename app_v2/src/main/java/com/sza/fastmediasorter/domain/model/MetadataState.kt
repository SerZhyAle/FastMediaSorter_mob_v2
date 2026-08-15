package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Persistence-grade state of per-file metadata enrichment (S0248 Phase 1/3).
 *
 * Stored as TEXT in `file_metadata_cache.metadataState`. Legacy rows that
 * predate migration 30→31 are mapped to [COMPLETE] via the SQL DEFAULT - see
 * [com.sza.fastmediasorter.data.local.db.AppDatabase.MIGRATION_30_31].
 *
 * Lifecycle:
 *  - Successful enrichment within the per-file budget → [COMPLETE].
 *  - Per-file timeout in EXIF/ID3/video probe → [PARTIAL]. Retried on every
 *    subsequent scan; cached so the row remains visible meanwhile.
 *  - Hard error (extractor exception other than timeout) → [BROKEN]. NOT
 *    retried automatically - only on an explicit user-triggered refresh.
 *
 * The strategic spec also referenced a transient PENDING state for listing-only
 * rows that have not yet entered enrichment. PENDING is an in-memory marker on
 * [com.sza.fastmediasorter.domain.model.MediaFile]; it is NOT persisted, hence
 * no enum entry here - the cache row only exists after enrichment has been
 * attempted at least once.
 */
// S1661: rides in every cached file list row as MediaFile.metadataState, so a blob written by one release is
// read back by the next one. Gson takes the written value from the constant itself, so MediaFile's keep rule
// does not reach these names - they are pinned here instead. The wire names deliberately repeat the constant
// names so cache blobs written before this change keep parsing.
enum class MetadataState {
    /** Extraction succeeded within the per-file budget. Default for legacy rows. */
    @SerializedName("COMPLETE")
    COMPLETE,

    /**
     * Per-file timeout elapsed; the row contains whatever the extractor managed
     * to capture before the budget cut it off (often listing fields only).
     * Re-attempted on every scan.
     */
    @SerializedName("PARTIAL")
    PARTIAL,

    /**
     * Extractor raised a hard error (corrupt file, unreadable stream, format
     * exception). Retried only when the caller explicitly opts in via
     * `forceRefresh = true` (typically a user-triggered refresh action).
     */
    @SerializedName("BROKEN")
    BROKEN,
}
