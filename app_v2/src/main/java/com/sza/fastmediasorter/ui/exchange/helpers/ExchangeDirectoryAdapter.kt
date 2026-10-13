package com.sza.fastmediasorter.ui.exchange.helpers

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemExchangeDirectoryRowBinding
import com.sza.fastmediasorter.domain.model.ExchangeAttachmentState
import com.sza.fastmediasorter.domain.model.ExchangeResourceAccess
import com.sza.fastmediasorter.ui.exchange.ExchangeDirectoryRow

/**
 * The rows of "From my devices": a device line with presence in words, never colour alone (S4116 3.3), then its
 * resources with a mark for those that can be attached, then the attached resources the listing no longer shows.
 */
class ExchangeDirectoryAdapter(
    private val rows: List<ExchangeDirectoryRow>,
    private val onToggle: (ExchangeDirectoryRow.Resource, Boolean) -> Unit,
) : RecyclerView.Adapter<ExchangeDirectoryAdapter.Holder>() {

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemExchangeDirectoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position])

    inner class Holder(private val binding: ItemExchangeDirectoryRowBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ExchangeDirectoryRow) {
            val context = binding.root.context
            binding.rowExchangeDirectory.setOnClickListener(null)
            binding.rowExchangeDirectory.isClickable = false
            binding.rowExchangeDirectory.isFocusable = false
            binding.checkExchangeDirectory.isVisible = false
            binding.textExchangeDirectoryTitle.setTypeface(null, Typeface.NORMAL)
            when (row) {
                is ExchangeDirectoryRow.Device -> bindDevice(context, row)
                is ExchangeDirectoryRow.Resource -> bindResource(context, row)
                ExchangeDirectoryRow.AttachedHeader ->
                    bindHeader(context.getString(R.string.exchange_directory_attached_section))
                is ExchangeDirectoryRow.Attached -> {
                    binding.textExchangeDirectoryTitle.text = row.name
                    binding.textExchangeDirectoryDetails.text = context.getString(stateText(row.state))
                }
            }
        }

        private fun bindHeader(title: String) {
            binding.textExchangeDirectoryTitle.text = title
            binding.textExchangeDirectoryTitle.setTypeface(null, Typeface.BOLD)
            binding.textExchangeDirectoryDetails.text = null
        }

        private fun bindDevice(context: Context, row: ExchangeDirectoryRow.Device) {
            bindHeader(row.title)
            val presence = context.getString(
                if (row.online) R.string.exchange_devices_device_online else R.string.exchange_devices_device_offline
            )
            binding.textExchangeDirectoryDetails.text = row.product
                ?.let { context.getString(R.string.exchange_devices_device_details, it, presence) }
                ?: presence
        }

        private fun bindResource(context: Context, row: ExchangeDirectoryRow.Resource) {
            binding.textExchangeDirectoryTitle.text = row.item.record.name
            binding.textExchangeDirectoryDetails.text = context.getString(resourceText(row))
            binding.checkExchangeDirectory.isVisible = true
            binding.checkExchangeDirectory.isChecked = row.attached || row.checked
            binding.checkExchangeDirectory.isEnabled = row.checkable
            if (!row.checkable) return
            binding.rowExchangeDirectory.isClickable = true
            binding.rowExchangeDirectory.isFocusable = true
            binding.rowExchangeDirectory.setOnClickListener { onToggle(row, !row.checked) }
        }

        private fun resourceText(row: ExchangeDirectoryRow.Resource): Int = when {
            row.attached -> R.string.exchange_directory_resource_attached
            row.item.access == ExchangeResourceAccess.PIN_LOCKED -> R.string.exchange_directory_resource_locked
            row.item.access == ExchangeResourceAccess.NO_ACCESS -> R.string.exchange_directory_resource_no_access
            !row.item.online -> R.string.exchange_directory_resource_offline
            else -> R.string.exchange_devices_device_online
        }

        private fun stateText(state: ExchangeAttachmentState): Int = when (state) {
            ExchangeAttachmentState.NO_LONGER_PUBLISHED -> R.string.exchange_directory_no_longer_published
            ExchangeAttachmentState.UNAVAILABLE -> R.string.exchange_directory_unavailable
            ExchangeAttachmentState.AVAILABLE -> R.string.exchange_directory_resource_attached
        }
    }
}
