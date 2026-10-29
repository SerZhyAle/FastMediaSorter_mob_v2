package com.sza.fastmediasorter.ui.scheduledops

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemScheduledRunHistoryBinding

/**
 * One displayed run of a schedule (S4478).
 *
 * @property startedAt the run's start in epoch milliseconds; two runs never start at the same moment,
 *   so it identifies the row.
 * @property headline time, outcome and plain-language reason, already worded for the interface language.
 * @property details the technical text behind the "Details" button, or null when the run left none.
 */
data class ScheduledRunHistoryRow(
    val startedAt: Long,
    val headline: String,
    val details: String?,
)

/** Lists a schedule's recorded runs, one row each, with a "Details" button on rows that have a detail text. */
class ScheduledRunHistoryAdapter(
    private val onDetails: (String) -> Unit,
) : ListAdapter<ScheduledRunHistoryRow, ScheduledRunHistoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemScheduledRunHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val b: ItemScheduledRunHistoryBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(row: ScheduledRunHistoryRow) {
            b.tvRunHeadline.text = row.headline
            val details = row.details
            b.btnRunDetails.isVisible = details != null
            // Rebound on every bind so a recycled holder never opens the previous row's text.
            b.btnRunDetails.setOnClickListener { details?.let(onDetails) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ScheduledRunHistoryRow>() {
        override fun areItemsTheSame(old: ScheduledRunHistoryRow, new: ScheduledRunHistoryRow) =
            old.startedAt == new.startedAt

        override fun areContentsTheSame(old: ScheduledRunHistoryRow, new: ScheduledRunHistoryRow) = old == new
    }
}
