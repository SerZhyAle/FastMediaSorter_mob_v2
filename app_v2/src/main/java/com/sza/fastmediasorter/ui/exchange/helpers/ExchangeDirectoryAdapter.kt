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
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.model.ExchangeResourceAccess
import com.sza.fastmediasorter.ui.exchange.ExchangeDirectoryRow
import timber.log.Timber

/**
 * The rows of "From my devices": a device line with presence in words, never colour alone (S4116 3.3), then its
 * resources with a mark for those that can be attached, then the attached resources the listing no longer shows.
 * A protected resource names its PIN in words (S4201); an attached one that waits for its PIN opens the prompt.
 */
class ExchangeDirectoryAdapter(
    private val rows: List<ExchangeDirectoryRow>,
    private val onToggle: (ExchangeDirectoryRow.Resource, Boolean) -> Unit,
    private val onPinNeeded: (ExchangeDirectoryRow.Resource) -> Unit,
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
            // Item DA of DEVICE-EXCHANGE: a message of the shared table is shown whole, never joined with other text.
            if (row.paused) {
                Timber.d("S4225: device row shows share-paused")
                binding.textExchangeDirectoryDetails.setText(R.string.exchange_share_paused)
                return
            }
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
            if (!row.checkable && !row.asksPin) return
            binding.rowExchangeDirectory.isClickable = true
            binding.rowExchangeDirectory.isFocusable = true
            binding.rowExchangeDirectory.setOnClickListener {
                if (row.asksPin) onPinNeeded(row) else onToggle(row, !row.checked)
            }
        }

        private fun resourceText(row: ExchangeDirectoryRow.Resource): Int {
            val access = row.item.access
            val online = row.item.online
            return when {
                row.asksPin -> R.string.exchange_directory_resource_pin_needed
                row.attached -> R.string.exchange_directory_resource_attached
                access == ExchangeResourceAccess.PIN_LOCKED ->
                    ExchangeMessageText.what(ExchangeMessage.PROTECTION_UNSUPPORTED)
                access == ExchangeResourceAccess.NO_ACCESS -> R.string.exchange_directory_resource_no_access
                access == ExchangeResourceAccess.PIN_NO_ACCESS && online && row.signInNeeded ->
                    R.string.exchange_directory_resource_pin_sign_in
                access == ExchangeResourceAccess.PIN_NO_ACCESS && online ->
                    R.string.exchange_directory_resource_no_access
                !online && row.item.paused -> R.string.exchange_share_paused
                !online -> R.string.exchange_directory_resource_offline
                access == ExchangeResourceAccess.PIN_REQUIRED ->
                    ExchangeMessageText.what(ExchangeMessage.RESOURCE_LOCKED)
                else -> R.string.exchange_devices_device_online
            }
        }

        private fun stateText(state: ExchangeAttachmentState): Int = when (state) {
            ExchangeAttachmentState.NO_LONGER_PUBLISHED -> R.string.exchange_directory_no_longer_published
            ExchangeAttachmentState.UNAVAILABLE -> R.string.exchange_directory_unavailable
            ExchangeAttachmentState.AVAILABLE -> R.string.exchange_directory_resource_attached
        }
    }
}
