package com.sza.fastmediasorter.ui.statistics

import android.content.Context
import android.os.Build
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.stats.StatsCategory
import com.sza.fastmediasorter.domain.stats.StatsKey
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import com.sza.fastmediasorter.domain.stats.StatsSnapshot
import com.sza.fastmediasorter.domain.usecase.GetStatisticsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Serializes the all-time statistics snapshot to a readable plain-text (TXT) report for the
 * "Send to author" / "Export" actions (S0473 Phase 05, strategic §0 refinement 4, ADR-1).
 *
 * Lives in the presentation layer: it resolves localized `statistics_*` label resources and reuses
 * [StatisticsRowFormatter], so the TXT mirrors exactly what the dashboard shows - summary totals,
 * type distribution, every visible category metric and the always-on baseline.
 *
 * Privacy invariant (ADR-1, §3.2): the report contains NO user identifiers. The closing technical
 * context is deliberately only the four build/device constants ADR-1 permits (app version, flavor,
 * device model, Android version) - never the full device dump (fingerprint, local IPs, device name,
 * installer) that other diagnostics expose.
 */
class BuildStatisticsReportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getStatistics: GetStatisticsUseCase,
) {

    suspend operator fun invoke(): String {
        val snapshot = getStatistics()
        val available = getStatistics.availableCategories()
        return buildString {
            appendHeader()
            appendSummary(snapshot)
            appendDistribution(snapshot)
            appendCategories(snapshot, available)
            appendBaseline(snapshot)
            appendTechnicalContext()
        }
    }

    private fun StringBuilder.appendHeader() {
        appendLine(string(R.string.statistics_title))
        appendLine(string(R.string.statistics_report_all_time))
        appendLine()
    }

    private fun StringBuilder.appendSummary(snapshot: StatsSnapshot) {
        appendLine(string(R.string.statistics_report_section_summary))
        appendValueLine(R.string.statistics_card_sorted, formatCount(snapshot.sortedFilesCount()))
        appendValueLine(R.string.statistics_card_freed, formatBytes(snapshot.count(StatsKey.BYTES_FREED)))
        val playerMs = snapshot.count(StatsKey.VIDEO_WATCH_MS) + snapshot.count(StatsKey.AUDIO_LISTEN_MS)
        appendValueLine(R.string.statistics_card_player_time, formatDuration(playerMs))
        appendLine()
    }

    private fun StringBuilder.appendDistribution(snapshot: StatsSnapshot) {
        val slices = StatisticsRowFormatter.buildDistribution(snapshot.byType)
        if (slices.isEmpty()) return
        appendLine(string(R.string.statistics_distribution_title))
        for (slice in slices) {
            val value = context.getString(
                R.string.statistics_distribution_value_format,
                slice.percent,
                formatCount(slice.value),
            )
            appendValueLine(slice.type.labelRes(), value)
        }
        appendLine()
    }

    private fun StringBuilder.appendCategories(snapshot: StatsSnapshot, available: Set<StatsCategory>) {
        for (category in StatsCategory.entries) {
            if (category !in available) continue
            val lines = categoryLines(category, snapshot)
            if (lines.isEmpty()) continue
            appendLine(string(category.titleRes()))
            lines.forEach { appendLine(it) }
            appendLine()
        }
    }

    /**
     * One labeled line per visible metric, hiding zero rows exactly like the dashboard (the
     * always-on USAGE markers in [appendBaseline] are shown unconditionally there instead).
     */
    private fun categoryLines(category: StatsCategory, snapshot: StatsSnapshot): List<String> = when (category) {
        StatsCategory.OPERATIONS -> listOfNotNull(
            countLine(R.string.statistics_metric_files_copied, snapshot, StatsKey.FILES_COPIED, StatsKey.BYTES_COPIED, MetricUnit.BYTES),
            countLine(R.string.statistics_metric_files_moved, snapshot, StatsKey.FILES_MOVED, StatsKey.BYTES_MOVED, MetricUnit.BYTES),
            countLine(R.string.statistics_metric_files_deleted, snapshot, StatsKey.FILES_DELETED, StatsKey.BYTES_FREED, MetricUnit.BYTES),
            countLine(R.string.statistics_metric_files_archived, snapshot, StatsKey.FILES_ARCHIVED),
            countLine(R.string.statistics_metric_files_extracted, snapshot, StatsKey.FILES_EXTRACTED),
            countLine(R.string.statistics_metric_folders_created, snapshot, StatsKey.FOLDERS_CREATED),
            countLine(R.string.statistics_metric_duplicate_scans, snapshot, StatsKey.DUPLICATE_SCANS),
            countLine(R.string.statistics_metric_duplicates_removed, snapshot, StatsKey.DUPLICATES_REMOVED, StatsKey.DUPLICATE_BYTES_FREED, MetricUnit.BYTES),
        )

        StatsCategory.CAPTURE -> listOfNotNull(
            countLine(R.string.statistics_metric_photos_captured, snapshot, StatsKey.PHOTOS_CAPTURED),
            countLine(R.string.statistics_metric_videos_recorded, snapshot, StatsKey.VIDEOS_RECORDED),
            countLine(R.string.statistics_metric_voice_notes, snapshot, StatsKey.VOICE_NOTES),
            countLine(R.string.statistics_metric_screenshots, snapshot, StatsKey.SCREENSHOTS),
        )

        StatsCategory.VIEWING -> listOfNotNull(
            countLine(R.string.statistics_metric_images_viewed, snapshot, StatsKey.IMAGES_VIEWED),
            countLine(R.string.statistics_metric_videos_watched, snapshot, StatsKey.VIDEOS_WATCHED, StatsKey.VIDEO_WATCH_MS, MetricUnit.DURATION),
            countLine(R.string.statistics_metric_audio_played, snapshot, StatsKey.AUDIO_PLAYED, StatsKey.AUDIO_LISTEN_MS, MetricUnit.DURATION),
            countLine(R.string.statistics_metric_documents_opened, snapshot, StatsKey.DOCUMENTS_OPENED, StatsKey.DOCUMENT_PAGES, MetricUnit.COUNT),
            countLine(R.string.statistics_metric_frames_exported, snapshot, StatsKey.FRAMES_EXPORTED),
        )

        StatsCategory.EDITING -> listOfNotNull(
            countLine(R.string.statistics_metric_image_edits, snapshot, StatsKey.IMAGE_EDITS),
            countLine(R.string.statistics_metric_drawings, snapshot, StatsKey.DRAWINGS),
            countLine(R.string.statistics_metric_notes, snapshot, StatsKey.NOTES),
        )

        StatsCategory.SOURCES -> listOfNotNull(
            countLine(R.string.statistics_metric_sources_connected, snapshot, StatsKey.SOURCES_CONNECTED),
        )

        // USAGE rows are emitted by appendBaseline so the always-on markers always show.
        StatsCategory.USAGE -> emptyList()
    }

    private fun StringBuilder.appendBaseline(snapshot: StatsSnapshot) {
        val baseline = snapshot.baseline
        appendLine(string(R.string.statistics_category_usage))
        appendValueLine(R.string.statistics_metric_launch_count, formatCount(baseline.launchCount))
        if (baseline.firstLaunchEpochMs > 0L) {
            appendValueLine(R.string.statistics_metric_first_launch, StatisticsRowFormatter.formatDate(baseline.firstLaunchEpochMs))
        }
        if (baseline.firstInstallVersion.isNotBlank()) {
            appendValueLine(R.string.statistics_metric_install_version, baseline.firstInstallVersion)
        }
        countLine(R.string.statistics_metric_sessions, snapshot, StatsKey.SESSIONS)?.let { appendLine(it) }
        countLine(R.string.statistics_metric_active_time, snapshot, StatsKey.ACTIVE_MS, unit = MetricUnit.DURATION)?.let { appendLine(it) }
        appendLine()
    }

    private fun StringBuilder.appendTechnicalContext() {
        appendLine(string(R.string.statistics_report_section_tech))
        appendValueLine(R.string.statistics_report_tech_app_version, BuildConfig.VERSION_NAME)
        appendValueLine(R.string.statistics_report_tech_flavor, BuildConfig.FLAVOR)
        appendValueLine(R.string.statistics_report_tech_device, Build.MODEL.orEmptyUnknown())
        appendValueLine(
            R.string.statistics_report_tech_android,
            "${Build.VERSION.RELEASE.orEmptyUnknown()} (API ${Build.VERSION.SDK_INT})",
        )
    }

    /** A "Label: value" metric line, hiding itself when the primary value is zero. */
    private fun countLine(
        labelRes: Int,
        snapshot: StatsSnapshot,
        key: StatsKey,
        secondary: StatsKey? = null,
        unit: MetricUnit = MetricUnit.COUNT,
    ): String? {
        val value = snapshot.count(key)
        if (value == 0L) return null
        val secondaryValue = secondary?.let { snapshot.count(it) }?.takeIf { it > 0L }
        val primary = formatCount(value)
        val rendered = if (secondaryValue != null) "$primary (${format(secondaryValue, unit)})" else primary
        return valueLine(string(labelRes), rendered)
    }

    private fun StringBuilder.appendValueLine(labelRes: Int, value: String) {
        appendLine(valueLine(string(labelRes), value))
    }

    private fun valueLine(label: String, value: String): String = "  $label: $value"

    private fun format(value: Long, unit: MetricUnit): String = when (unit) {
        MetricUnit.COUNT -> formatCount(value)
        MetricUnit.BYTES -> formatBytes(value)
        MetricUnit.DURATION -> formatDuration(value)
    }

    private fun formatCount(value: Long): String = StatisticsRowFormatter.formatCount(value)
    private fun formatBytes(value: Long): String = StatisticsRowFormatter.formatBytes(value)
    private fun formatDuration(value: Long): String = StatisticsRowFormatter.formatDuration(context, value)

    private fun string(resId: Int): String = context.getString(resId)

    private fun String?.orEmptyUnknown(): String =
        this?.takeIf { it.isNotBlank() } ?: context.getString(R.string.statistics_report_unknown)

    private fun StatsMediaType.labelRes(): Int = when (this) {
        StatsMediaType.IMAGE -> R.string.statistics_type_image
        StatsMediaType.VIDEO -> R.string.statistics_type_video
        StatsMediaType.AUDIO -> R.string.statistics_type_audio
        StatsMediaType.DOCUMENT -> R.string.statistics_type_document
        StatsMediaType.OTHER -> R.string.statistics_type_other
    }

    private fun StatsCategory.titleRes(): Int = when (this) {
        StatsCategory.OPERATIONS -> R.string.statistics_category_operations
        StatsCategory.CAPTURE -> R.string.statistics_category_capture
        StatsCategory.VIEWING -> R.string.statistics_category_viewing
        StatsCategory.EDITING -> R.string.statistics_category_editing
        StatsCategory.SOURCES -> R.string.statistics_category_sources
        StatsCategory.USAGE -> R.string.statistics_category_usage
    }

    /** Secondary-value rendering unit, mirroring the dashboard's MetricFormat for counts/bytes/time. */
    private enum class MetricUnit { COUNT, BYTES, DURATION }
}
