package com.sza.fastmediasorter.ui.dialog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemDestinationButtonBinding
import com.sza.fastmediasorter.domain.model.MediaResource

class DestinationAdapter(
    private val onDestinationClick: (MediaResource) -> Unit
) : ListAdapter<MediaResource, DestinationAdapter.DestinationViewHolder>(DestinationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DestinationViewHolder {
        val binding = ItemDestinationButtonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DestinationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DestinationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DestinationViewHolder(
        private val binding: ItemDestinationButtonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(destination: MediaResource) {
            binding.btnDestination.apply {
                text = destination.name
                setBackgroundColor(destination.destinationColor)
                setOnClickListener { onDestinationClick(destination) }
            }
        }
    }

    private class DestinationDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean {
            return oldItem == newItem
        }
    }
}
