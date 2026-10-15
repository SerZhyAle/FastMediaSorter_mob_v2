package com.sza.fastmediasorter.ui.exchange.drive

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemDriveChannelEntryBinding
import com.sza.fastmediasorter.domain.model.transfer.DriveListingEntry
import java.time.Instant

/** S4231: one row of a folder browsed via Google Drive; [entry] null is the ".." row above the folder. */
data class DriveEntryRow(val entry: DriveListingEntry?, val checked: Boolean = false)

/** S4231: folder entries via Google Drive - names, sizes and dates only, no thumbnails (contract item CO). */
class DriveChannelEntryAdapter(
    private val onUp: () -> Unit,
    private val onFolder: (String) -> Unit,
    private val onFile: (String) -> Unit,
) : ListAdapter<DriveEntryRow, DriveChannelEntryAdapter.Holder>(Diff) {

    class Holder(val binding: ItemDriveChannelEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemDriveChannelEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val context = holder.binding.root.context
        val entry = row.entry
        holder.binding.checkDriveEntry.isInvisible = entry == null || entry.dir
        holder.binding.checkDriveEntry.isChecked = row.checked
        when {
            entry == null -> {
                holder.binding.tvDriveEntryName.text = context.getString(R.string.drive_channel_browse_up)
                holder.binding.tvDriveEntryDetails.text = ""
                holder.binding.root.contentDescription = context.getString(R.string.drive_channel_browse_up)
                holder.binding.root.setOnClickListener { onUp() }
            }
            entry.dir -> {
                holder.binding.tvDriveEntryName.text = entry.name
                holder.binding.tvDriveEntryDetails.text = dateOf(entry)
                holder.binding.root.contentDescription =
                    context.getString(R.string.drive_channel_browse_folder_desc, entry.name)
                holder.binding.root.setOnClickListener { onFolder(entry.name) }
            }
            else -> {
                val size = Formatter.formatShortFileSize(context, entry.size)
                holder.binding.tvDriveEntryName.text = entry.name
                holder.binding.tvDriveEntryDetails.text = listOf(size, dateOf(entry)).filter { it.isNotEmpty() }
                    .joinToString(", ")
                holder.binding.root.contentDescription =
                    context.getString(R.string.drive_channel_browse_file_desc, entry.name, size)
                holder.binding.root.setOnClickListener { onFile(entry.name) }
            }
        }
    }

    // The producer's clock, shown as given; a value that is not RFC 3339 is left out rather than guessed.
    private fun dateOf(entry: DriveListingEntry): String =
        runCatching { Instant.parse(entry.modifiedAt).toString().take(DATE_AND_MINUTES).replace('T', ' ') }
            .getOrDefault("")

    private object Diff : DiffUtil.ItemCallback<DriveEntryRow>() {
        override fun areItemsTheSame(oldItem: DriveEntryRow, newItem: DriveEntryRow): Boolean =
            oldItem.entry?.name == newItem.entry?.name && oldItem.entry?.dir == newItem.entry?.dir

        override fun areContentsTheSame(oldItem: DriveEntryRow, newItem: DriveEntryRow): Boolean = oldItem == newItem
    }

    private companion object {
        const val DATE_AND_MINUTES = 16
    }
}
