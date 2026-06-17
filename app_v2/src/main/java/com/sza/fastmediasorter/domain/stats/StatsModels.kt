package com.sza.fastmediasorter.domain.stats

/**
 * Read-side models for the local usage-statistics feature (S0473).
 *
 * All values are all-time totals (no period buckets - strategic ADR-7). No Android
 * dependencies: these are pure domain holders consumed by the dashboard and the report.
 */

/** Logical grouping of metrics shown as collapsible sections in the dashboard. */
enum class StatsCategory { OPERATIONS, CAPTURE, VIEWING, EDITING, SOURCES, USAGE }

/** Media-type buckets for the action x type matrix (strategic §5.4 group G). */
enum class StatsMediaType { IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER }

/**
 * Scalar counter identifiers. The sink folds events into deltas keyed by these, and the
 * aggregate store persists one preference per key. New metrics append here without
 * touching existing callers (strategic §5.3 extensibility).
 */
enum class StatsKey {
    FILES_COPIED, BYTES_COPIED,
    FILES_MOVED, BYTES_MOVED,
    FILES_DELETED, BYTES_FREED,
    FILES_ARCHIVED, FILES_EXTRACTED,
    FOLDERS_CREATED,
    DUPLICATE_SCANS, DUPLICATES_REMOVED, DUPLICATE_BYTES_FREED,
    PHOTOS_CAPTURED, VIDEOS_RECORDED, VOICE_NOTES, SCREENSHOTS,
    IMAGES_VIEWED,
    VIDEOS_WATCHED, VIDEO_WATCH_MS,
    AUDIO_PLAYED, AUDIO_LISTEN_MS,
    DOCUMENTS_OPENED, DOCUMENT_PAGES,
    FRAMES_EXPORTED,
    IMAGE_EDITS, DRAWINGS, NOTES,
    SOURCES_CONNECTED,
    SESSIONS, ACTIVE_MS
}

/** Per-media-type processing counts for the action x type matrix. */
data class MediaActionCounts(
    val copied: Long = 0L,
    val moved: Long = 0L,
    val deleted: Long = 0L,
    val totalBytes: Long = 0L
) {
    operator fun plus(other: MediaActionCounts): MediaActionCounts = MediaActionCounts(
        copied = copied + other.copied,
        moved = moved + other.moved,
        deleted = deleted + other.deleted,
        totalBytes = totalBytes + other.totalBytes
    )
}

/** Always-on baseline metrics, accrued regardless of the opt-in toggle (strategic ADR-2). */
data class StatsBaselineSnapshot(
    val firstLaunchEpochMs: Long = 0L,
    val firstInstallVersion: String = "",
    val firstInstallFlavor: String = "",
    val launchCount: Long = 0L
)

/** Additive batch of increments produced by the sink and applied to the aggregate store. */
data class StatsAggregateDelta(
    val counters: Map<StatsKey, Long> = emptyMap(),
    val byType: Map<StatsMediaType, MediaActionCounts> = emptyMap()
) {
    val isEmpty: Boolean get() = counters.isEmpty() && byType.isEmpty()

    operator fun plus(other: StatsAggregateDelta): StatsAggregateDelta {
        val mergedCounters = counters.toMutableMap()
        other.counters.forEach { (k, v) -> mergedCounters[k] = (mergedCounters[k] ?: 0L) + v }
        val mergedByType = byType.toMutableMap()
        other.byType.forEach { (k, v) -> mergedByType[k] = (mergedByType[k] ?: MediaActionCounts()) + v }
        return StatsAggregateDelta(mergedCounters, mergedByType)
    }
}

/** All detailed (opt-in) values read back from the aggregate store. */
data class StatsAggregateValues(
    val counters: Map<StatsKey, Long> = emptyMap(),
    val byType: Map<StatsMediaType, MediaActionCounts> = emptyMap()
)

/**
 * Full all-time snapshot the dashboard renders: always-on baseline plus the detailed
 * opt-in counters and the action x type matrix.
 */
data class StatsSnapshot(
    val baseline: StatsBaselineSnapshot,
    val counters: Map<StatsKey, Long>,
    val byType: Map<StatsMediaType, MediaActionCounts>
) {
    fun count(key: StatsKey): Long = counters[key] ?: 0L

    /** True when no detailed activity has been recorded yet (only baseline present). */
    val hasNoDetailedActivity: Boolean
        get() = counters.values.all { it == 0L } && byType.isEmpty()
}
