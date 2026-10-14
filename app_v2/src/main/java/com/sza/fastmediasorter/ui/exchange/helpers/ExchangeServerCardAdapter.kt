package com.sza.fastmediasorter.ui.exchange.helpers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemExchangeServerCardBinding
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.ui.exchange.ExchangeServerCard
import com.sza.fastmediasorter.ui.exchange.ExchangeServerStatus

/** One card per exchange account entry on the "My devices" screen (S4156 strategic 6.3). */
class ExchangeServerCardAdapter(
    private val onOpen: (DeviceExchangeAccountEntry) -> Unit,
    private val onMenu: (View, DeviceExchangeAccountEntry) -> Unit,
) : ListAdapter<ExchangeServerCard, ExchangeServerCardAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemExchangeServerCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemExchangeServerCardBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: ExchangeServerCard) {
            val context = binding.root.context
            val entry = card.entry
            binding.textExchangeServerAddress.text = address(entry)
            binding.textExchangeServerLogin.text = context.getString(R.string.exchange_devices_card_login, entry.login)
            binding.textExchangeServerStatus.text = when (val status = card.status) {
                ExchangeServerStatus.Checking -> context.getString(R.string.exchange_devices_status_checking)
                is ExchangeServerStatus.Online ->
                    context.getString(R.string.exchange_devices_status_online, status.deviceCount)
                is ExchangeServerStatus.Refused -> context.getString(status.text)
                ExchangeServerStatus.Unreachable -> context.getString(R.string.exchange_devices_status_unreachable)
            }
            binding.textExchangeServerOutgoing.isVisible = entry.outgoing
            binding.root.setOnClickListener { onOpen(entry) }
            binding.btnExchangeServerMenu.setOnClickListener { onMenu(it, entry) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ExchangeServerCard>() {
        override fun areItemsTheSame(old: ExchangeServerCard, new: ExchangeServerCard) =
            old.entry.identity == new.entry.identity

        override fun areContentsTheSame(old: ExchangeServerCard, new: ExchangeServerCard) = old == new
    }

    companion object {
        /** The address as the user typed it: IPv6 in brackets, so the port stays readable. */
        fun address(entry: DeviceExchangeAccountEntry): String =
            if (':' in entry.host) "[${entry.host}]:${entry.port}" else "${entry.host}:${entry.port}"
    }
}
