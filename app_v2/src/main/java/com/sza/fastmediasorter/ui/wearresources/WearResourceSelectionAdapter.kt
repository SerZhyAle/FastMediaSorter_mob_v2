package com.sza.fastmediasorter.ui.wearresources

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemWearResourceSelectionBinding
import com.sza.fastmediasorter.domain.model.MediaResource

private const val PAYLOAD_SELECTION = "payload_selection"

/**
 * Resource rows with a tick each. Selection is passed in rather than held here so the persisted set
 * stays the single source of truth - a row never remembers a tick the repository did not accept.
 */
class WearResourceSelectionAdapter(
    private val onSelectionChanged: (MediaResource, Boolean) -> Unit
) : ListAdapter<MediaResource, WearResourceSelectionAdapter.ViewHolder>(ResourceDiffCallback()) {

    private var selectedIds: Set<Long> = emptySet()

    fun setSelectedIds(ids: Set<Long>) {
        val previous = selectedIds
        selectedIds = ids
        currentList.forEachIndexed { index, resource ->
            if ((resource.id in previous) != (resource.id in ids)) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWearResourceSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SELECTION }) {
            holder.bindSelection(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(
        private val binding: ItemWearResourceSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(resource: MediaResource) {
            binding.tvResourceName.text = resource.name
            binding.tvResourcePath.text = resource.path
            bindSelection(resource)
            binding.rowRoot.setOnClickListener {
                onSelectionChanged(resource, resource.id !in selectedIds)
            }
        }

        fun bindSelection(resource: MediaResource) {
            val selected = resource.id in selectedIds
            binding.cbSelected.isChecked = selected
            binding.rowRoot.contentDescription = resource.name
            binding.rowRoot.isSelected = selected
        }
    }

    private class ResourceDiffCallback : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource): Boolean =
            oldItem.name == newItem.name && oldItem.path == newItem.path
    }
}
