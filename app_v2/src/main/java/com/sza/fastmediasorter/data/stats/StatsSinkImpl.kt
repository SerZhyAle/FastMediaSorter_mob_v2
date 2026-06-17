package com.sza.fastmediasorter.data.stats

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.local.preferences.StatsAggregateDataStore
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.MediaActionCounts
import com.sza.fastmediasorter.domain.stats.StatsAggregateDelta
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsKey
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.stats.FileOpAction
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.ViewKind
import com.sza.fastmediasorter.domain.stats.EditKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory, gate-aware implementation of [StatsSink] (S0473).
 *
 * - No-op when the opt-in toggle is off: the cached [enabled] flag is updated from the settings
 *   flow, and [record] returns immediately when collection is disabled (strategic ADR-5).
 * - When enabled, events fold into an in-memory [StatsAggregateDelta] and a debounced flush
 *   writes the whole batch with one [StatsAggregateDataStore.apply] (strategic ADR-6) - a batch
 *   operation thus produces a single disk write, never one-per-file.
 */
@Singleton
class StatsSinkImpl @Inject constructor(
    settingsRepository: SettingsRepository,
    private val aggregate: StatsAggregateDataStore,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) : StatsSink {

    @Volatile
    private var enabled: Boolean = false

    private val lock = Any()
    private var pending = StatsAggregateDelta()
    private var flushJob: Job? = null

    init {
        scope.launch {
            settingsRepository.getSettings().collect { enabled = it.enableStatistics }
        }
    }

    override fun record(event: StatsEvent) {
        if (!enabled) return
        val delta = event.toDelta()
        if (delta.isEmpty) return
        synchronized(lock) { pending += delta }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            flushNow()
        }
    }

    /** Persists the accumulated batch immediately. Called on debounce and on app-background. */
    override suspend fun flushNow() {
        val toApply = synchronized(lock) {
            val drained = pending
            pending = StatsAggregateDelta()
            drained
        }
        if (!toApply.isEmpty) {
            Timber.d("S0473: flushing ${toApply.counters.size} stat counters to disk")
            withContext(io) { aggregate.apply(toApply) }
        }
    }

    private fun StatsEvent.toDelta(): StatsAggregateDelta = when (this) {
        is StatsEvent.FileOp -> fileOpDelta(this)
        is StatsEvent.DuplicatesRemoved -> counters(
            StatsKey.DUPLICATES_REMOVED to count,
            StatsKey.DUPLICATE_BYTES_FREED to bytesFreed
        )
        StatsEvent.DuplicateScanRun -> counters(StatsKey.DUPLICATE_SCANS to 1L)
        is StatsEvent.Capture -> counters(
            when (kind) {
                CaptureKind.PHOTO -> StatsKey.PHOTOS_CAPTURED
                CaptureKind.VIDEO -> StatsKey.VIDEOS_RECORDED
                CaptureKind.VOICE -> StatsKey.VOICE_NOTES
                CaptureKind.SCREENSHOT -> StatsKey.SCREENSHOTS
            } to 1L
        )
        is StatsEvent.View -> when (kind) {
            ViewKind.IMAGE -> counters(StatsKey.IMAGES_VIEWED to count)
            ViewKind.VIDEO -> counters(StatsKey.VIDEOS_WATCHED to count, StatsKey.VIDEO_WATCH_MS to durationMs)
            ViewKind.AUDIO -> counters(StatsKey.AUDIO_PLAYED to count, StatsKey.AUDIO_LISTEN_MS to durationMs)
            ViewKind.DOCUMENT -> counters(StatsKey.DOCUMENTS_OPENED to count, StatsKey.DOCUMENT_PAGES to pages)
            ViewKind.FRAME_EXPORT -> counters(StatsKey.FRAMES_EXPORTED to count)
        }
        is StatsEvent.Edit -> counters(
            when (kind) {
                EditKind.IMAGE_EDIT -> StatsKey.IMAGE_EDITS
                EditKind.DRAWING -> StatsKey.DRAWINGS
                EditKind.NOTE -> StatsKey.NOTES
            } to 1L
        )
        is StatsEvent.SourceConnected -> counters(StatsKey.SOURCES_CONNECTED to 1L)
        is StatsEvent.Session -> counters(StatsKey.SESSIONS to 1L, StatsKey.ACTIVE_MS to activeMs)
    }

    private fun fileOpDelta(op: StatsEvent.FileOp): StatsAggregateDelta {
        val counters = mutableMapOf<StatsKey, Long>()
        val byType = mutableMapOf<StatsMediaType, MediaActionCounts>()
        when (op.action) {
            FileOpAction.COPY -> {
                counters[StatsKey.FILES_COPIED] = op.count
                counters[StatsKey.BYTES_COPIED] = op.bytes
                byType[op.type] = MediaActionCounts(copied = op.count, totalBytes = op.bytes)
            }
            FileOpAction.MOVE -> {
                counters[StatsKey.FILES_MOVED] = op.count
                counters[StatsKey.BYTES_MOVED] = op.bytes
                byType[op.type] = MediaActionCounts(moved = op.count, totalBytes = op.bytes)
            }
            FileOpAction.DELETE -> {
                counters[StatsKey.FILES_DELETED] = op.count
                counters[StatsKey.BYTES_FREED] = op.bytes
                byType[op.type] = MediaActionCounts(deleted = op.count, totalBytes = op.bytes)
            }
            FileOpAction.ARCHIVE -> counters[StatsKey.FILES_ARCHIVED] = op.count
            FileOpAction.EXTRACT -> counters[StatsKey.FILES_EXTRACTED] = op.count
            FileOpAction.CREATE_FOLDER -> counters[StatsKey.FOLDERS_CREATED] = op.count
        }
        return StatsAggregateDelta(counters = counters, byType = byType)
    }

    private fun counters(vararg pairs: Pair<StatsKey, Long>): StatsAggregateDelta =
        StatsAggregateDelta(counters = pairs.toMap())

    private companion object {
        const val FLUSH_DEBOUNCE_MS = 2_500L
    }
}
