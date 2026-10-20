package com.sza.fastmediasorter.ui.exchange.helpers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemExchangeDeviceBinding
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice

/**
 * The devices of one account (5.2) with presence as words, never colour alone. The own device carries no
 * revoke button: it leaves the account through "Remove server", which also forgets the token.
 * S4348: the dialog keeps one adapter for its lifetime and the manager submit()s into it, so an emission
 * rebinds only the rows that changed instead of throwing away the holder pool and the scroll position.
 */
class ExchangeDeviceAdapter(
    private val ownDeviceId: String,
    private val onRevoke: (SftpRendezvousDevice) -> Unit,
) : ListAdapter<SftpRendezvousDevice, ExchangeDeviceAdapter.Holder>(Diff) {

    private var notices: Map<String, ExchangeMessage> = emptyMap()

    /** The notices arrive with the same load as the devices, so refreshing them beside submitList is enough. */
    fun submit(devices: List<SftpRendezvousDevice>, notices: Map<String, ExchangeMessage>) {
        this.notices = notices
        submitList(devices)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemExchangeDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemExchangeDeviceBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: SftpRendezvousDevice) {
            val context = binding.root.context
            val own = device.deviceId == ownDeviceId
            binding.textExchangeDeviceName.text =
                if (own) context.getString(R.string.exchange_devices_device_this) else device.deviceName
            val presence = context.getString(
                if (device.isOnline) {
                    R.string.exchange_devices_device_online
                } else {
                    R.string.exchange_devices_device_offline
                }
            )
            // Item DA of DEVICE-EXCHANGE: a message of the shared table is shown whole, never joined with other text.
            val notice = notices[device.deviceId]
            binding.textExchangeDeviceDetails.text = when {
                notice != null -> ExchangeMessageText.twoParts(context, notice)
                device.isSharePaused -> context.getString(R.string.exchange_share_paused)
                else -> context.getString(R.string.exchange_devices_device_details, device.product, presence)
            }
            binding.btnExchangeDeviceRevoke.isVisible = !own
            binding.btnExchangeDeviceRevoke.contentDescription =
                context.getString(R.string.exchange_devices_revoke_confirm_title, device.deviceName)
            binding.btnExchangeDeviceRevoke.setOnClickListener { onRevoke(device) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SftpRendezvousDevice>() {
        override fun areItemsTheSame(oldItem: SftpRendezvousDevice, newItem: SftpRendezvousDevice): Boolean =
            oldItem.deviceId == newItem.deviceId

        override fun areContentsTheSame(oldItem: SftpRendezvousDevice, newItem: SftpRendezvousDevice): Boolean =
            oldItem == newItem
    }
}
