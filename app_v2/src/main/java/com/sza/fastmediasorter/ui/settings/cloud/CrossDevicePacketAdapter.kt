package com.sza.fastmediasorter.ui.settings.cloud

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemCrossDevicePacketBinding
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry

/** S4231: one row of the queue - a packet offered to this device, or one of its own sends still waiting. */
data class CrossDevicePacketRow(val entry: CrossDevicePacketEntry, val outgoing: Boolean)

/**
 * S4231: the queue rows in two lines - who the packet is from or for, then its size, age and, for an own
 * send, that it is still waiting (contract DEVICE-EXCHANGE items CS and CT: waiting is never delivery).
 */
class CrossDevicePacketAdapter(
    private val context: Context,
    private val onClick: (CrossDevicePacketRow) -> Unit,
) : ListAdapter<CrossDevicePacketRow, CrossDevicePacketAdapter.Holder>(Diff) {

    class Holder(val binding: ItemCrossDevicePacketBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemCrossDevicePacketBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val title = titleOf(row)
        val details = detailsOf(row)
        holder.binding.tvPacketTitle.text = title
        holder.binding.tvPacketDetails.text = details
        holder.binding.root.contentDescription = "$title. $details"
        holder.binding.root.setOnClickListener { onClick(row) }
    }

    fun titleOf(row: CrossDevicePacketRow): String {
        val manifest = row.entry.manifest
        return when {
            !row.outgoing -> context.getString(R.string.drive_channel_row_from, manifest.senderDeviceName)
            manifest.targetDeviceName != null ->
                context.getString(R.string.drive_channel_row_for_device, manifest.targetDeviceName)
            else -> context.getString(R.string.drive_channel_row_for_any)
        }
    }

    private fun detailsOf(row: CrossDevicePacketRow): String {
        val size = Formatter.formatShortFileSize(context, row.entry.manifest.totalSizeBytes)
        val age = DateUtils.getRelativeTimeSpanString(row.entry.createdTimeMs).toString()
        val sizeAndAge = context.getString(R.string.drive_channel_row_details, size, age)
        return if (row.outgoing) {
            context.getString(
                R.string.drive_channel_row_details,
                sizeAndAge,
                context.getString(R.string.drive_channel_row_waiting)
            )
        } else {
            sizeAndAge
        }
    }

    private object Diff : DiffUtil.ItemCallback<CrossDevicePacketRow>() {
        override fun areItemsTheSame(oldItem: CrossDevicePacketRow, newItem: CrossDevicePacketRow): Boolean =
            oldItem.entry.packetId == newItem.entry.packetId

        override fun areContentsTheSame(oldItem: CrossDevicePacketRow, newItem: CrossDevicePacketRow): Boolean =
            oldItem == newItem
    }
}
