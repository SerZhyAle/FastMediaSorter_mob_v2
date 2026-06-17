package com.sza.fastmediasorter.domain.stats

/**
 * Write contract for local usage statistics (S0473).
 *
 * [record] is intentionally non-suspending and safe to call from any thread or hot path - it
 * only enqueues an event. The implementation no-ops entirely when the opt-in toggle is off
 * (strategic ADR-5) and batches accepted events in memory, flushing to disk on a debounce or
 * on app-background (strategic ADR-6), so callers never wait on I/O.
 *
 * Note: the `Sxxxx:` debug-tag prefix is NOT used in this permanent code - it is reserved for
 * temporary BlockNeedUserTest probes.
 */
interface StatsSink {
    fun record(event: StatsEvent)

    /** Persists any buffered events immediately. Called on app-background (strategic §3.2). */
    suspend fun flushNow()
}

/**
 * Events emitted from existing completion points. New event types append here without touching
 * existing callers - this is the metric-catalog extension seam (strategic §5.3).
 */
sealed interface StatsEvent {
    /** A completed file operation over [count] files of [type], moving/freeing [bytes]. */
    data class FileOp(
        val action: FileOpAction,
        val type: StatsMediaType,
        val count: Long,
        val bytes: Long
    ) : StatsEvent

    /** Duplicates removed by the dedup flow (count + space freed). */
    data class DuplicatesRemoved(val count: Long, val bytesFreed: Long) : StatsEvent

    /** One duplicate-detection scan completed. */
    data object DuplicateScanRun : StatsEvent

    /** A media capture completed. */
    data class Capture(val kind: CaptureKind) : StatsEvent

    /** A view/playback completed; [durationMs] for video/audio, [pages] for documents. */
    data class View(
        val kind: ViewKind,
        val durationMs: Long = 0L,
        val pages: Long = 0L,
        val count: Long = 1L
    ) : StatsEvent

    /** An edit/create action completed. */
    data class Edit(val kind: EditKind) : StatsEvent

    /** A remote source connection succeeded. */
    data class SourceConnected(val type: StatsMediaType? = null) : StatsEvent

    /** A foreground session ended after [activeMs] of active time. */
    data class Session(val activeMs: Long) : StatsEvent
}

enum class FileOpAction { COPY, MOVE, DELETE, ARCHIVE, EXTRACT, CREATE_FOLDER }

enum class CaptureKind { PHOTO, VIDEO, VOICE, SCREENSHOT }

enum class ViewKind { IMAGE, VIDEO, AUDIO, DOCUMENT, FRAME_EXPORT }

enum class EditKind { IMAGE_EDIT, DRAWING, NOTE }
