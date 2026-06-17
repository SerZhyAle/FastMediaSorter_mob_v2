package com.sza.fastmediasorter.ui.statistics

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.domain.stats.StatsCategory

/**
 * Render model for the statistics dashboard (S0473 Phase 04).
 *
 * The ViewModel maps the all-time [com.sza.fastmediasorter.domain.stats.StatsSnapshot] to a flat,
 * already-filtered list of [StatisticsListItem]s the adapter renders 1:1. All visibility rules
 * (flavor-unavailable categories hidden, zero rows hidden except always-on baseline) are applied
 * here, so neither the adapter nor the Activity carries business logic. Values stay raw `Long`s
 * tagged with a [MetricFormat] - [StatisticsRowFormatter] turns them into strings at bind time,
 * keeping locale/resources out of the ViewModel. All-time totals only (no period state, ADR-7).
 */
data class StatisticsUiState(
    val loading: Boolean = true,
    val isEmpty: Boolean = false,
    val items: List<StatisticsListItem> = emptyList(),
)

/** How a raw metric value should be rendered by [StatisticsRowFormatter]. */
enum class MetricFormat { COUNT, BYTES, DURATION_MS, DATE, TEXT }

/**
 * One-shot side effects emitted by [StatisticsViewModel] (S0473 Phase 05). Each carries the ready
 * TXT [report] for [StatisticsReportShareManager]; the ViewModel stays free of Intent/FileProvider.
 */
sealed interface StatisticsEvent {
    val report: String

    /** Compose an author email with the report attached (user confirms send). */
    data class SendToAuthor(override val report: String) : StatisticsEvent

    /** Save/share the report so the user can keep it. */
    data class Export(override val report: String) : StatisticsEvent
}

/** Heterogeneous dashboard rows. Stable [id]s drive DiffUtil; sections own a collapse flag. */
sealed interface StatisticsListItem {
    val id: String

    /** Summary card: a big primary value over a label, with an icon (strategic §5.5). */
    data class SummaryCard(
        override val id: String,
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
        val value: Long,
        val format: MetricFormat,
    ) : StatisticsListItem

    /** The type-distribution bar with its value-bearing legend (not color-only). */
    data class Distribution(
        @StringRes val labelRes: Int,
        val slices: List<DistributionSlice>,
    ) : StatisticsListItem {
        override val id: String get() = "distribution"
    }

    /** Collapsible category header. [expanded] toggles visibility of its [StatisticsListItem.MetricRow]s. */
    data class SectionHeader(
        val category: StatsCategory,
        @StringRes val titleRes: Int,
        val expanded: Boolean,
    ) : StatisticsListItem {
        override val id: String get() = "header_${category.name}"
    }

    /**
     * A metric line: icon + label + primary value, with an optional secondary value
     * (e.g. bytes or duration shown beside a count).
     */
    data class MetricRow(
        override val id: String,
        val category: StatsCategory,
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int,
        val value: Long,
        val format: MetricFormat,
        val textValue: String? = null,
        val secondaryValue: Long? = null,
        val secondaryFormat: MetricFormat? = null,
    ) : StatisticsListItem

    /** Friendly empty-state shown when only baseline exists yet (strategic §5.5). */
    data class EmptyState(@StringRes val textRes: Int) : StatisticsListItem {
        override val id: String get() = "empty_state"
    }

    /** Privacy footnote shown under the list (data stays on device; user sends the email). */
    data class PrivacyNote(@StringRes val textRes: Int) : StatisticsListItem {
        override val id: String get() = "privacy_note"
    }
}
