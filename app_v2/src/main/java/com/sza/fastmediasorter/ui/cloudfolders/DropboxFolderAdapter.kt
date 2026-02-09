package com.sza.fastmediasorter.ui.cloudfolders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemDropboxFolderBinding

class DropboxFolderAdapter(
    private val onFolderSelect: (CloudFolderItem) -> Unit,
    private val onFolderNavigate: (CloudFolderItem) -> Unit,
    private val onNavigateBack: () -> Unit,
    private val isRootLevel: () -> Boolean
) : ListAdapter<CloudFolderItem, DropboxFolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemDropboxFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding, onFolderSelect, onFolderNavigate, onNavigateBack, isRootLevel)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FolderViewHolder(
        private val binding: ItemDropboxFolderBinding,
        private val onFolderSelect: (CloudFolderItem) -> Unit,
        private val onFolderNavigate: (CloudFolderItem) -> Unit,
        private val onNavigateBack: () -> Unit,
        private val isRootLevel: () -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: CloudFolderItem) {
            binding.tvFolderName.text = folder.name
            
            // Show "Back" button only if not at root level
            binding.btnBack.isVisible = !isRootLevel()
            
            // Click handlers
            binding.root.setOnClickListener {
                onFolderSelect(folder)
            }
            
            binding.btnSelect.setOnClickListener {
                onFolderSelect(folder)
            }
            
            binding.btnEnter.setOnClickListener {
                onFolderNavigate(folder)
            }
            
            binding.btnBack.setOnClickListener {
                onNavigateBack()
            }
        }
    }

    class FolderDiffCallback : DiffUtil.ItemCallback<CloudFolderItem>() {
        override fun areItemsTheSame(oldItem: CloudFolderItem, newItem: CloudFolderItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CloudFolderItem, newItem: CloudFolderItem): Boolean {
            return oldItem == newItem
        }
    }
}
