package com.sza.fastmediasorter.ui.cloudfolders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class CloudFolderAdapter(
    private val inflate: (LayoutInflater, ViewGroup, Boolean) -> CloudFolderItemBinding,
    private val onFolderSelect: (CloudFolderItem) -> Unit,
    private val onFolderNavigate: (CloudFolderItem) -> Unit,
    private val onNavigateBack: () -> Unit,
    private val isRootLevel: () -> Boolean
) : ListAdapter<CloudFolderItem, CloudFolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding, onFolderSelect, onFolderNavigate, onNavigateBack, isRootLevel)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FolderViewHolder(
        private val binding: CloudFolderItemBinding,
        private val onFolderSelect: (CloudFolderItem) -> Unit,
        private val onFolderNavigate: (CloudFolderItem) -> Unit,
        private val onNavigateBack: () -> Unit,
        private val isRootLevel: () -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: CloudFolderItem) {
            binding.tvFolderName.text = folder.name
            binding.btnBack.isVisible = !isRootLevel()

            // S0289: a click / ENTER on the row navigates INTO the folder (file-browser
            // convention) so a D-pad / keyboard user descends with the natural gesture. The
            // explicit Select button stays the confirm-as-destination action.
            binding.root.setOnClickListener { onFolderNavigate(folder) }
            binding.btnSelect.setOnClickListener { onFolderSelect(folder) }
            binding.btnEnter.setOnClickListener { onFolderNavigate(folder) }
            binding.btnBack.setOnClickListener { onNavigateBack() }
        }
    }

    class FolderDiffCallback : DiffUtil.ItemCallback<CloudFolderItem>() {
        override fun areItemsTheSame(oldItem: CloudFolderItem, newItem: CloudFolderItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CloudFolderItem, newItem: CloudFolderItem) =
            oldItem == newItem
    }
}
