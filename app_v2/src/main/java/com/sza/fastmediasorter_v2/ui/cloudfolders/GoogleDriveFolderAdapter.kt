package com.sza.fastmediasorter_v2.ui.cloudfolders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.databinding.ItemGoogleDriveFolderBinding

class GoogleDriveFolderAdapter(
    private val onFolderClick: (CloudFolderItem) -> Unit
) : ListAdapter<CloudFolderItem, GoogleDriveFolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemGoogleDriveFolderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FolderViewHolder(binding, onFolderClick)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FolderViewHolder(
        private val binding: ItemGoogleDriveFolderBinding,
        private val onFolderClick: (CloudFolderItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: CloudFolderItem) {
            binding.tvFolderName.text = folder.name
            binding.root.isSelected = folder.isSelected
            
            binding.ivFolderIcon.setImageResource(
                if (folder.isSelected) R.drawable.ic_folder_open_24
                else R.drawable.ic_folder_24
            )

            binding.root.setOnClickListener {
                onFolderClick(folder)
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
