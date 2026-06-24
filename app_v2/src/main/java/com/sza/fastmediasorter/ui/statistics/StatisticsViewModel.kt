package com.sza.fastmediasorter.ui.statistics

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.stats.StatsCategory
import com.sza.fastmediasorter.domain.stats.StatsKey
import com.sza.fastmediasorter.domain.stats.StatsSnapshot
import com.sza.fastmediasorter.domain.usecase.GetStatisticsUseCase
import com.sza.fastmediasorter.ui.statistics.StatisticsListItem.MetricRow
import com.sza.fastmediasorter.ui.statistics.StatisticsListItem.SectionHeader
import com.sza.fastmediasorter.ui.statistics.StatisticsListItem.SummaryCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the statistics dashboard (S0473 Phase 04).
 *
 * Loads the all-time snapshot off the UI thread, maps it to a flat, pre-filtered
 * [StatisticsUiState] and exposes it as a [StateFlow]. Category collapse state is held here and
 * re-folded into the list on toggle. No period or reset state exists (ADR-4, ADR-7) - this screen
 * shows all-time totals and never mutates the data.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getStatistics: GetStatisticsUseCase,
    private val buildReport: BuildStatisticsReportUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    /** One-shot side effects the Activity hands to [StatisticsReportShareManager] (no Intent here). */
    private val _events = Channel<StatisticsEvent>(Channel.BUFFERED)
    val events: Flow<StatisticsEvent> = _events.receiveAsFlow()

    // Snapshot kept so a collapse toggle can re-render without re-reading storage.
    private var snapshot: StatsSnapshot? = null
    private val collapsedCategories = mutableSetOf<StatsCategory>()

    init {
        load()
    }

    /** Build the TXT report off the UI thread, then emit it for the author-email flow. */
    fun onSendToAuthor() = emitReport { StatisticsEvent.SendToAuthor(it) }

    /** Build the TXT report off the UI thread, then emit it for the export/save flow. */
    fun onExport() = emitReport { StatisticsEvent.Export(it) }

    private inline fun emitReport(crossinline toEvent: (String) -> StatisticsEvent) {
        viewModelScope.launch {
            val report = withContext(ioDispatcher) { buildReport() }
            _events.send(toEvent(report))
        }
    }

    private fun load() {
        viewModelScope.launch {
            val loaded = withContext(ioDispatcher) { getStatistics() }
            snapshot = loaded
            Timber.d("StatisticsViewModel: snapshot loaded, empty=${loaded.hasNoDetailedActivity}")
            render()
        }
    }

    /** Flip a section between expanded and collapsed, then re-emit the visible list. */
    fun toggleCategory(category: StatsCategory) {
        if (!collapsedCategories.add(category)) collapsedCategories.remove(category)
        render()
    }

    private fun render() {
        val current = snapshot ?: return
        _uiState.update {
            StatisticsUiState(
                loading = false,
                isEmpty = current.hasNoDetailedActivity,
                items = buildItems(current),
            )
        }
    }

    private fun buildItems(snapshot: StatsSnapshot): List<StatisticsListItem> {
        val items = mutableListOf<StatisticsListItem>()
        if (snapshot.hasNoDetailedActivity) {
            // Friendly placeholder leads; the always-on baseline (USAGE) still renders below it.
            items += StatisticsListItem.EmptyState(R.string.statistics_empty)
        }
        items += summaryCards(snapshot)

        val distribution = StatisticsRowFormatter.buildDistribution(snapshot.byType)
        if (distribution.isNotEmpty()) {
            items += StatisticsListItem.Distribution(R.string.statistics_distribution_title, distribution)
        }

        if (!snapshot.hasNoDetailedActivity || hasVisibleBaseline(snapshot)) {
            val available = getStatistics.availableCategories()
            for (category in StatsCategory.entries) {
                if (category !in available) continue
                val rows = rowsFor(category, snapshot)
                if (rows.isEmpty()) continue
                val expanded = !collapsedCategories.contains(category)
                items += SectionHeader(category, category.titleRes(), expanded)
                if (expanded) items += rows
            }
        }

        items += StatisticsListItem.PrivacyNote(R.string.statistics_privacy_note)
        return items
    }

    private fun summaryCards(snapshot: StatsSnapshot): List<SummaryCard> = listOf(
        SummaryCard(
            id = "card_sorted",
            labelRes = R.string.statistics_card_sorted,
            iconRes = R.drawable.ic_move,
            value = snapshot.sortedFilesCount(),
            format = MetricFormat.COUNT,
        ),
        SummaryCard(
            id = "card_freed",
            labelRes = R.string.statistics_card_freed,
            iconRes = R.drawable.ic_delete_sweep,
            value = snapshot.count(StatsKey.BYTES_FREED),
            format = MetricFormat.BYTES,
        ),
        SummaryCard(
            id = "card_player_time",
            labelRes = R.string.statistics_card_player_time,
            iconRes = R.drawable.ic_play,
            value = snapshot.count(StatsKey.VIDEO_WATCH_MS) + snapshot.count(StatsKey.AUDIO_LISTEN_MS),
            format = MetricFormat.DURATION_MS,
        ),
        // S0499: 4th card completes a 2x2 grid on phones (card span = 2 portrait). "Viewed" is the
        // browsing headline: total media opened across images, videos, audio and documents.
        SummaryCard(
            id = "card_viewed",
            labelRes = R.string.statistics_card_viewed,
            iconRes = R.drawable.ic_slideshow,
            value = snapshot.count(StatsKey.IMAGES_VIEWED) +
                snapshot.count(StatsKey.VIDEOS_WATCHED) +
                snapshot.count(StatsKey.AUDIO_PLAYED) +
                snapshot.count(StatsKey.DOCUMENTS_OPENED),
            format = MetricFormat.COUNT,
        ),
    )

    private fun rowsFor(category: StatsCategory, snapshot: StatsSnapshot): List<MetricRow> = when (category) {
        StatsCategory.OPERATIONS -> listOfNotNull(
            countRow(category, "op_copied", R.drawable.ic_copy, R.string.statistics_metric_files_copied,
                snapshot, StatsKey.FILES_COPIED, secondary = StatsKey.BYTES_COPIED, secondaryFormat = MetricFormat.BYTES),
            countRow(category, "op_moved", R.drawable.ic_move, R.string.statistics_metric_files_moved,
                snapshot, StatsKey.FILES_MOVED, secondary = StatsKey.BYTES_MOVED, secondaryFormat = MetricFormat.BYTES),
            countRow(category, "op_deleted", R.drawable.ic_delete, R.string.statistics_metric_files_deleted,
                snapshot, StatsKey.FILES_DELETED, secondary = StatsKey.BYTES_FREED, secondaryFormat = MetricFormat.BYTES),
            countRow(category, "op_archived", R.drawable.ic_folder, R.string.statistics_metric_files_archived,
                snapshot, StatsKey.FILES_ARCHIVED),
            countRow(category, "op_extracted", R.drawable.ic_folder_open_24, R.string.statistics_metric_files_extracted,
                snapshot, StatsKey.FILES_EXTRACTED),
            countRow(category, "op_folders", R.drawable.ic_folder_24, R.string.statistics_metric_folders_created,
                snapshot, StatsKey.FOLDERS_CREATED),
            countRow(category, "op_dup_scans", R.drawable.ic_history, R.string.statistics_metric_duplicate_scans,
                snapshot, StatsKey.DUPLICATE_SCANS),
            countRow(category, "op_dup_removed", R.drawable.ic_delete_sweep, R.string.statistics_metric_duplicates_removed,
                snapshot, StatsKey.DUPLICATES_REMOVED, secondary = StatsKey.DUPLICATE_BYTES_FREED, secondaryFormat = MetricFormat.BYTES),
            countRow(category, "op_renamed", R.drawable.ic_rename, R.string.statistics_metric_files_renamed,
                snapshot, StatsKey.FILES_RENAMED),
            countRow(category, "op_fav_added", R.drawable.ic_star_filled, R.string.statistics_metric_favorites_added,
                snapshot, StatsKey.FAVORITES_ADDED),
            countRow(category, "op_fav_removed", R.drawable.ic_star_outline, R.string.statistics_metric_favorites_removed,
                snapshot, StatsKey.FAVORITES_REMOVED),
            countRow(category, "op_scheduled_runs", R.drawable.ic_schedule, R.string.statistics_metric_scheduled_runs,
                snapshot, StatsKey.SCHEDULED_TASKS_RUN),
            countRow(category, "op_scheduled_files", R.drawable.ic_schedule, R.string.statistics_metric_scheduled_files,
                snapshot, StatsKey.SCHEDULED_TASK_FILES_PROCESSED),
            countRow(category, "op_undo", R.drawable.ic_undo, R.string.statistics_metric_undo,
                snapshot, StatsKey.UNDO_OPERATIONS),
        )

        StatsCategory.CAPTURE -> listOfNotNull(
            countRow(category, "cap_photos", R.drawable.ic_camera_capture, R.string.statistics_metric_photos_captured,
                snapshot, StatsKey.PHOTOS_CAPTURED),
            countRow(category, "cap_videos", R.drawable.ic_video, R.string.statistics_metric_videos_recorded,
                snapshot, StatsKey.VIDEOS_RECORDED),
            countRow(category, "cap_voice", R.drawable.ic_microphone, R.string.statistics_metric_voice_notes,
                snapshot, StatsKey.VOICE_NOTES),
            countRow(category, "cap_screenshots", R.drawable.ic_image, R.string.statistics_metric_screenshots,
                snapshot, StatsKey.SCREENSHOTS),
        )

        StatsCategory.VIEWING -> listOfNotNull(
            countRow(category, "view_images", R.drawable.ic_image, R.string.statistics_metric_images_viewed,
                snapshot, StatsKey.IMAGES_VIEWED),
            countRow(category, "view_videos", R.drawable.ic_video, R.string.statistics_metric_videos_watched,
                snapshot, StatsKey.VIDEOS_WATCHED, secondary = StatsKey.VIDEO_WATCH_MS, secondaryFormat = MetricFormat.DURATION_MS),
            countRow(category, "view_audio", R.drawable.ic_audio_track, R.string.statistics_metric_audio_played,
                snapshot, StatsKey.AUDIO_PLAYED, secondary = StatsKey.AUDIO_LISTEN_MS, secondaryFormat = MetricFormat.DURATION_MS),
            countRow(category, "view_docs", R.drawable.ic_info, R.string.statistics_metric_documents_opened,
                snapshot, StatsKey.DOCUMENTS_OPENED, secondary = StatsKey.DOCUMENT_PAGES, secondaryFormat = MetricFormat.COUNT),
            countRow(category, "view_frames", R.drawable.ic_video, R.string.statistics_metric_frames_exported,
                snapshot, StatsKey.FRAMES_EXPORTED),
            countRow(category, "view_slideshow_sessions", R.drawable.ic_slideshow, R.string.statistics_metric_slideshow_sessions,
                snapshot, StatsKey.SLIDESHOW_SESSIONS),
            countRow(category, "view_slideshow_images", R.drawable.ic_slideshow, R.string.statistics_metric_slideshow_images,
                snapshot, StatsKey.SLIDESHOW_IMAGES_SHOWN),
            countRow(category, "view_gif_frames", R.drawable.ic_gif, R.string.statistics_metric_gif_frames_saved,
                snapshot, StatsKey.GIF_FRAMES_SAVED),
            countRow(category, "view_streams_audio", R.drawable.ic_audio_track, R.string.statistics_metric_streams_audio_played,
                snapshot, StatsKey.STREAMS_AUDIO_PLAYED),
            countRow(category, "view_streams_video", R.drawable.ic_video, R.string.statistics_metric_streams_video_played,
                snapshot, StatsKey.STREAMS_VIDEO_PLAYED),
            countRow(category, "view_ocr", R.drawable.ic_translate, R.string.statistics_metric_ocr_scans,
                snapshot, StatsKey.OCR_SCANS),
        )

        StatsCategory.EDITING -> listOfNotNull(
            countRow(category, "edit_image_edits", R.drawable.ic_image, R.string.statistics_metric_image_edits,
                snapshot, StatsKey.IMAGE_EDITS),
            countRow(category, "edit_drawings", R.drawable.ic_edit_20, R.string.statistics_metric_drawings,
                snapshot, StatsKey.DRAWINGS),
            countRow(category, "edit_notes", R.drawable.ic_edit_20, R.string.statistics_metric_notes,
                snapshot, StatsKey.NOTES),
        )

        StatsCategory.SOURCES -> listOfNotNull(
            countRow(category, "src_connected", R.drawable.ic_cloud_download, R.string.statistics_metric_sources_connected,
                snapshot, StatsKey.SOURCES_CONNECTED),
            countRow(category, "src_streams_added", R.drawable.ic_cloud_download, R.string.statistics_metric_streams_added,
                snapshot, StatsKey.STREAMS_ADDED),
            countRow(category, "src_playlists_imported", R.drawable.ic_cloud_download, R.string.statistics_metric_playlists_imported,
                snapshot, StatsKey.PLAYLISTS_IMPORTED),
        )

        StatsCategory.USAGE -> buildUsageRows(snapshot)
    }

    /**
     * USAGE keeps the always-on baseline rows visible even at zero (launch count, first launch,
     * install version), while still hiding zero detailed rows (sessions, active time).
     */
    private fun buildUsageRows(snapshot: StatsSnapshot): List<MetricRow> {
        val rows = mutableListOf<MetricRow>()
        rows += MetricRow(
            id = "use_launches",
            category = StatsCategory.USAGE,
            iconRes = R.drawable.ic_history,
            labelRes = R.string.statistics_metric_launch_count,
            value = snapshot.baseline.launchCount,
            format = MetricFormat.COUNT,
        )
        if (snapshot.baseline.firstLaunchEpochMs > 0L) {
            rows += MetricRow(
                id = "use_first_launch",
                category = StatsCategory.USAGE,
                iconRes = R.drawable.ic_history,
                labelRes = R.string.statistics_metric_first_launch,
                value = snapshot.baseline.firstLaunchEpochMs,
                format = MetricFormat.DATE,
            )
        }
        if (snapshot.baseline.firstInstallVersion.isNotBlank()) {
            rows += MetricRow(
                id = "use_install_version",
                category = StatsCategory.USAGE,
                iconRes = R.drawable.ic_info,
                labelRes = R.string.statistics_metric_install_version,
                value = 0L,
                format = MetricFormat.TEXT,
                textValue = snapshot.baseline.firstInstallVersion,
            )
        }
        countRow(StatsCategory.USAGE, "use_sessions", R.drawable.ic_history, R.string.statistics_metric_sessions,
            snapshot, StatsKey.SESSIONS)?.let { rows += it }
        countRow(StatsCategory.USAGE, "use_active", R.drawable.ic_history, R.string.statistics_metric_active_time,
            snapshot, StatsKey.ACTIVE_MS, format = MetricFormat.DURATION_MS)?.let { rows += it }
        return rows
    }

    /** A count/value row that hides itself when its primary value is zero (zero-row rule). */
    private fun countRow(
        category: StatsCategory,
        id: String,
        @DrawableRes iconRes: Int,
        @StringRes labelRes: Int,
        snapshot: StatsSnapshot,
        key: StatsKey,
        format: MetricFormat = MetricFormat.COUNT,
        secondary: StatsKey? = null,
        secondaryFormat: MetricFormat? = null,
    ): MetricRow? {
        val value = snapshot.count(key)
        if (value == 0L) return null
        val secondaryValue = secondary?.let { snapshot.count(it) }?.takeIf { it > 0L }
        return MetricRow(
            id = id,
            category = category,
            iconRes = iconRes,
            labelRes = labelRes,
            value = value,
            format = format,
            secondaryValue = secondaryValue,
            secondaryFormat = if (secondaryValue != null) secondaryFormat else null,
        )
    }

    private fun hasVisibleBaseline(snapshot: StatsSnapshot): Boolean =
        snapshot.baseline.launchCount > 0L ||
            snapshot.baseline.firstLaunchEpochMs > 0L ||
            snapshot.baseline.firstInstallVersion.isNotBlank()

    @StringRes
    private fun StatsCategory.titleRes(): Int = when (this) {
        StatsCategory.OPERATIONS -> R.string.statistics_category_operations
        StatsCategory.CAPTURE -> R.string.statistics_category_capture
        StatsCategory.VIEWING -> R.string.statistics_category_viewing
        StatsCategory.EDITING -> R.string.statistics_category_editing
        StatsCategory.SOURCES -> R.string.statistics_category_sources
        StatsCategory.USAGE -> R.string.statistics_category_usage
    }
}
