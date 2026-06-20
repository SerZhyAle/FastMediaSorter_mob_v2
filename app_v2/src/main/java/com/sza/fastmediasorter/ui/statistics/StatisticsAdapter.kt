package com.sza.fastmediasorter.ui.statistics

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemStatsCardBinding
import com.sza.fastmediasorter.databinding.ItemStatsDistributionBinding
import com.sza.fastmediasorter.databinding.ItemStatsDistributionLegendBinding
import com.sza.fastmediasorter.databinding.ItemStatsMetricRowBinding
import com.sza.fastmediasorter.databinding.ItemStatsPrivacyNoteBinding
import com.sza.fastmediasorter.databinding.ViewStatsEmptyBinding
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import timber.log.Timber

/**
 * Multi-view-type list renderer for the statistics dashboard (S0473 Phase 04).
 *
 * Pure presentation: it binds a pre-built, already-filtered [StatisticsListItem] list from the
 * ViewModel and reports section-header taps via [onToggleSection]. No snapshot reads, no
 * formatting decisions beyond delegating to [StatisticsRowFormatter]. The grid span for each row
 * is resolved by [spanSizeFor] so summary cards sit side by side while everything else is full width.
 */
class StatisticsAdapter(
    private val spanCount: Int,
    private val onToggleSection: (com.sza.fastmediasorter.domain.stats.StatsCategory) -> Unit,
) : ListAdapter<StatisticsListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is StatisticsListItem.SummaryCard -> TYPE_CARD
        is StatisticsListItem.Distribution -> TYPE_DISTRIBUTION
        is StatisticsListItem.SectionHeader -> TYPE_HEADER
        is StatisticsListItem.MetricRow -> TYPE_ROW
        is StatisticsListItem.EmptyState -> TYPE_EMPTY
        is StatisticsListItem.PrivacyNote -> TYPE_PRIVACY
    }

    /** Cards occupy one cell; all other rows span the full grid width. */
    fun spanSizeFor(position: Int): Int =
        if (getItemViewType(position) == TYPE_CARD) 1 else spanCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CARD -> CardViewHolder(ItemStatsCardBinding.inflate(inflater, parent, false))
            TYPE_DISTRIBUTION -> DistributionViewHolder(ItemStatsDistributionBinding.inflate(inflater, parent, false))
            TYPE_HEADER -> HeaderViewHolder(createHeaderView(parent), onToggleSection)
            TYPE_ROW -> RowViewHolder(ItemStatsMetricRowBinding.inflate(inflater, parent, false))
            TYPE_EMPTY -> EmptyViewHolder(ViewStatsEmptyBinding.inflate(inflater, parent, false))
            else -> PrivacyViewHolder(ItemStatsPrivacyNoteBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is StatisticsListItem.SummaryCard -> (holder as CardViewHolder).bind(item)
            is StatisticsListItem.Distribution -> (holder as DistributionViewHolder).bind(item)
            is StatisticsListItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is StatisticsListItem.MetricRow -> (holder as RowViewHolder).bind(item)
            is StatisticsListItem.EmptyState -> Unit // static text, set in layout
            is StatisticsListItem.PrivacyNote -> Unit // static text, set in layout
        }
    }

    // S0535: section headers use the unified CollapsibleSectionHeader, absorbing the second
    // (bespoke list-header) collapsible mechanism. Built programmatically as Keybinding does.
    private fun createHeaderView(parent: ViewGroup): CollapsibleSectionHeader =
        CollapsibleSectionHeader(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

    private class CardViewHolder(
        private val binding: ItemStatsCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StatisticsListItem.SummaryCard) {
            binding.ivCardIcon.setImageResource(item.iconRes)
            binding.tvCardLabel.setText(item.labelRes)
            binding.tvCardValue.text = formatValue(binding.root, item.value, item.format, null)
        }
    }

    private class HeaderViewHolder(
        private val header: CollapsibleSectionHeader,
        private val onToggleSection: (com.sza.fastmediasorter.domain.stats.StatsCategory) -> Unit,
    ) : RecyclerView.ViewHolder(header) {
        fun bind(item: StatisticsListItem.SectionHeader) {
            Timber.d("S0535: statistics section header bound via unified widget")
            header.setTitle(item.titleRes)
            header.setExpanded(item.expanded, notify = false)
            // State is owned by the ViewModel list; the toggle rebuilds it and re-binds.
            header.setOnExpandedChangeListener { onToggleSection(item.category) }
        }
    }

    private class RowViewHolder(
        private val binding: ItemStatsMetricRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StatisticsListItem.MetricRow) {
            binding.ivMetricIcon.setImageResource(item.iconRes)
            binding.tvMetricLabel.setText(item.labelRes)
            binding.tvMetricValue.text = formatValue(binding.root, item.value, item.format, item.textValue)
            val secondary = item.secondaryValue
            val secondaryFormat = item.secondaryFormat
            if (secondary != null && secondaryFormat != null) {
                binding.tvMetricSecondary.text = formatValue(binding.root, secondary, secondaryFormat, null)
                binding.tvMetricSecondary.visibility = View.VISIBLE
            } else {
                binding.tvMetricSecondary.visibility = View.GONE
            }
        }
    }

    private class DistributionViewHolder(
        private val binding: ItemStatsDistributionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StatisticsListItem.Distribution) {
            binding.tvDistributionTitle.setText(item.labelRes)
            val context = binding.root.context
            val inflater = LayoutInflater.from(context)
            binding.barContainer.removeAllViews()
            binding.legendContainer.removeAllViews()
            for (slice in item.slices) {
                val colorRes = colorFor(slice.type)
                val color = ContextCompat.getColor(context, colorRes)

                val segment = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        slice.percent.coerceAtLeast(1).toFloat(),
                    ).also { it.marginEnd = HORIZONTAL_GAP_PX }
                    setBackgroundResource(R.drawable.bg_stats_distribution_segment)
                    backgroundTintList = ColorStateList.valueOf(color)
                }
                binding.barContainer.addView(segment)

                val legend = ItemStatsDistributionLegendBinding.inflate(inflater, binding.legendContainer, false)
                legend.viewLegendSwatch.backgroundTintList = ColorStateList.valueOf(color)
                legend.tvLegendLabel.setText(labelFor(slice.type))
                legend.tvLegendValue.text = context.getString(
                    R.string.statistics_distribution_value_format,
                    slice.percent,
                    StatisticsRowFormatter.formatCount(slice.value),
                )
                binding.legendContainer.addView(legend.root)
            }
        }

        @ColorRes
        private fun colorFor(type: StatsMediaType): Int = when (type) {
            StatsMediaType.IMAGE -> R.color.stats_type_image
            StatsMediaType.VIDEO -> R.color.stats_type_video
            StatsMediaType.AUDIO -> R.color.stats_type_audio
            StatsMediaType.DOCUMENT -> R.color.stats_type_document
            StatsMediaType.OTHER -> R.color.stats_type_other
        }

        private fun labelFor(type: StatsMediaType): Int = when (type) {
            StatsMediaType.IMAGE -> R.string.statistics_type_image
            StatsMediaType.VIDEO -> R.string.statistics_type_video
            StatsMediaType.AUDIO -> R.string.statistics_type_audio
            StatsMediaType.DOCUMENT -> R.string.statistics_type_document
            StatsMediaType.OTHER -> R.string.statistics_type_other
        }
    }

    private class EmptyViewHolder(binding: ViewStatsEmptyBinding) : RecyclerView.ViewHolder(binding.root)

    private class PrivacyViewHolder(binding: ItemStatsPrivacyNoteBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TYPE_CARD = 0
        private const val TYPE_DISTRIBUTION = 1
        private const val TYPE_HEADER = 2
        private const val TYPE_ROW = 3
        private const val TYPE_EMPTY = 4
        private const val TYPE_PRIVACY = 5

        private const val HORIZONTAL_GAP_PX = 4

        private fun formatValue(
            view: View,
            value: Long,
            format: MetricFormat,
            textValue: String?,
        ): String = when (format) {
            MetricFormat.COUNT -> StatisticsRowFormatter.formatCount(value)
            MetricFormat.BYTES -> StatisticsRowFormatter.formatBytes(value)
            MetricFormat.DURATION_MS -> StatisticsRowFormatter.formatDuration(view.context, value)
            MetricFormat.DATE -> StatisticsRowFormatter.formatDate(value)
            MetricFormat.TEXT -> textValue.orEmpty()
        }

        private val DIFF = object : DiffUtil.ItemCallback<StatisticsListItem>() {
            override fun areItemsTheSame(oldItem: StatisticsListItem, newItem: StatisticsListItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StatisticsListItem, newItem: StatisticsListItem): Boolean =
                oldItem == newItem
        }
    }
}
