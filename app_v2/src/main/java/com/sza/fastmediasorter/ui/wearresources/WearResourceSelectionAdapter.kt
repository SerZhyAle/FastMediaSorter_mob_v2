package com.sza.fastmediasorter.ui.wearresources

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemWearResourceGroupHeaderBinding
import com.sza.fastmediasorter.databinding.ItemWearResourceSelectionBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType

private const val PAYLOAD_SELECTION = "payload_selection"

sealed class WearResourceAdapterItem {
    data class Header(
        val category: ResourceCategory,
        val titleRes: Int,
        val isExpanded: Boolean,
        val count: Int
    ) : WearResourceAdapterItem()

    data class ResourceRow(
        val resource: MediaResource
    ) : WearResourceAdapterItem()
}

/**
 * Resource selection list supporting grouped category headers (Virtual, Internal, External)
 * and individual resource rows with recognizable icons.
 */
class WearResourceSelectionAdapter(
    private val onCategoryToggle: (ResourceCategory) -> Unit,
    private val onSelectionChanged: (MediaResource, Boolean) -> Unit
) : ListAdapter<WearResourceAdapterItem, RecyclerView.ViewHolder>(ResourceItemDiffCallback()) {

    private var selectedIds: Set<Long> = emptySet()

    fun setSelectedIds(ids: Set<Long>) {
        val previous = selectedIds
        selectedIds = ids
        currentList.forEachIndexed { index, item ->
            if (item is WearResourceAdapterItem.ResourceRow) {
                val resId = item.resource.id
                if ((resId in previous) != (resId in ids)) {
                    notifyItemChanged(index, PAYLOAD_SELECTION)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is WearResourceAdapterItem.Header -> VIEW_TYPE_HEADER
            is WearResourceAdapterItem.ResourceRow -> VIEW_TYPE_RESOURCE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemWearResourceGroupHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemWearResourceSelectionBinding.inflate(inflater, parent, false)
            ResourceViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is WearResourceAdapterItem.Header -> (holder as HeaderViewHolder).bind(item)
            is WearResourceAdapterItem.ResourceRow -> (holder as ResourceViewHolder).bind(item.resource)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SELECTION } && holder is ResourceViewHolder) {
            val item = getItem(position) as? WearResourceAdapterItem.ResourceRow
            item?.let { holder.bindSelection(it.resource) }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemWearResourceGroupHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: WearResourceAdapterItem.Header) {
            val title = binding.root.context.getString(header.titleRes)
            binding.tvHeaderTitle.text = "$title (${header.count})"
            val iconRes = if (header.isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            binding.ivExpandIndicator.setImageResource(iconRes)
            binding.headerRoot.setOnClickListener {
                onCategoryToggle(header.category)
            }
        }
    }

    inner class ResourceViewHolder(
        private val binding: ItemWearResourceSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(resource: MediaResource) {
            binding.tvResourceName.text = resource.name
            binding.tvResourcePath.text = resource.path
            binding.ivResourceIcon.setImageResource(getResourceIconRes(resource))
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

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_RESOURCE = 1

        fun getResourceIconRes(resource: MediaResource): Int {
            return when {
                resource.type == ResourceType.WEAR_WATCH -> R.drawable.ic_watch
                resource.type.isNetworkResource -> R.drawable.ic_cloud
                resource.profile == ResourceProfile.AUDIO_LIBRARY -> R.drawable.ic_audio
                resource.profile == ResourceProfile.VIDEO_LIBRARY -> R.drawable.ic_video
                resource.profile == ResourceProfile.PHOTO_STORAGE -> R.drawable.ic_image
                resource.profile == ResourceProfile.DOCUMENTS -> R.drawable.ic_virtual_docs
                else -> R.drawable.ic_folder
            }
        }
    }

    private class ResourceItemDiffCallback : DiffUtil.ItemCallback<WearResourceAdapterItem>() {
        override fun areItemsTheSame(
            oldItem: WearResourceAdapterItem,
            newItem: WearResourceAdapterItem
        ): Boolean = when {
            oldItem is WearResourceAdapterItem.Header && newItem is WearResourceAdapterItem.Header ->
                oldItem.category == newItem.category
            oldItem is WearResourceAdapterItem.ResourceRow && newItem is WearResourceAdapterItem.ResourceRow ->
                oldItem.resource.id == newItem.resource.id
            else -> false
        }

        override fun areContentsTheSame(
            oldItem: WearResourceAdapterItem,
            newItem: WearResourceAdapterItem
        ): Boolean = oldItem == newItem
    }
}
