package com.sza.fastmediasorter.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.data.ConnectionConfig
import com.sza.fastmediasorter.databinding.ItemSortDestinationBinding

class SortDestinationAdapter(
    private val onMoveUp: (ConnectionConfig) -> Unit,
    private val onMoveDown: (ConnectionConfig) -> Unit,
    private val onDelete: (ConnectionConfig) -> Unit,
) : ListAdapter<ConnectionConfig, SortDestinationAdapter.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding =
            ItemSortDestinationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ItemSortDestinationBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            config: ConnectionConfig,
            position: Int,
        ) {
            binding.orderText.text = position.toString()
            binding.sortNameText.text = config.sortName ?: ""

            // Display appropriate path based on connection type
            binding.folderAddressText.text =
                when (config.type) {
                    "LOCAL_CUSTOM", "LOCAL_STANDARD" -> "Local: ${config.localDisplayName ?: config.name}"
                    else -> "${config.serverAddress}\\${config.folderPath}"
                }

            binding.moveUpButton.isEnabled = position > 0
            binding.moveDownButton.isEnabled = position < itemCount - 1

            binding.moveUpButton.setOnClickListener { onMoveUp(config) }
            binding.moveDownButton.setOnClickListener { onMoveDown(config) }
            binding.deleteButton.setOnClickListener { onDelete(config) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ConnectionConfig>() {
        override fun areItemsTheSame(
            oldItem: ConnectionConfig,
            newItem: ConnectionConfig,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ConnectionConfig,
            newItem: ConnectionConfig,
        ): Boolean = oldItem == newItem
    }
}
