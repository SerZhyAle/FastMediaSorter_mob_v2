package com.sza.fastmediasorter.ui.settings.ticket

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemTicketRowBinding

/** S4554: one composer row - an attachment or an outbox packet; [removeLabel] is its TalkBack action. */
data class TicketRow(val id: String, val title: String, val subtitle: String?, val removeLabel: String)

/** S4554: the attachment list and the outbox list of the ticket composer. */
class TicketRowAdapter(private val onRemove: (TicketRow) -> Unit) :
    ListAdapter<TicketRow, TicketRowAdapter.Holder>(Diff) {

    class Holder(val binding: ItemTicketRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTicketRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        holder.binding.tvTicketRowTitle.text = row.title
        holder.binding.tvTicketRowSubtitle.text = row.subtitle
        holder.binding.tvTicketRowSubtitle.isVisible = !row.subtitle.isNullOrEmpty()
        holder.binding.btnTicketRowRemove.contentDescription = row.removeLabel
        holder.binding.btnTicketRowRemove.setOnClickListener { onRemove(row) }
    }

    private object Diff : DiffUtil.ItemCallback<TicketRow>() {
        override fun areItemsTheSame(oldItem: TicketRow, newItem: TicketRow) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TicketRow, newItem: TicketRow) = oldItem == newItem
    }
}
