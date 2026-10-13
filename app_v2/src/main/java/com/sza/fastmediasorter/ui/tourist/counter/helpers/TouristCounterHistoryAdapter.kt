package com.sza.fastmediasorter.ui.tourist.counter.helpers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemTouristCounterHistoryBinding
import com.sza.fastmediasorter.domain.model.tourist.TouristCounterRecord

/** S4184: one finished count per row - the number and the period it was counted over. */
class TouristCounterHistoryAdapter(
    private val formatMoment: (Long) -> String,
) : ListAdapter<TouristCounterRecord, TouristCounterHistoryAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTouristCounterHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val record = getItem(position)
        val context = holder.binding.root.context
        holder.binding.tvHistoryValue.text = record.value.toString()
        holder.binding.tvHistoryPeriod.text = context.getString(
            R.string.tourist_counter_history_period,
            formatMoment(record.startedAtMillis),
            formatMoment(record.endedAtMillis),
        )
    }

    class Holder(val binding: ItemTouristCounterHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TouristCounterRecord>() {
            override fun areItemsTheSame(old: TouristCounterRecord, new: TouristCounterRecord): Boolean =
                old.startedAtMillis == new.startedAtMillis && old.endedAtMillis == new.endedAtMillis

            override fun areContentsTheSame(old: TouristCounterRecord, new: TouristCounterRecord): Boolean =
                old == new
        }
    }
}
