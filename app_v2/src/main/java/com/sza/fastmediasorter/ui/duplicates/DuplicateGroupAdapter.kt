package com.sza.fastmediasorter.ui.duplicates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemDuplicateFileBinding
import com.sza.fastmediasorter.databinding.ItemDuplicateGroupBinding
import com.sza.fastmediasorter.domain.model.DuplicateGroup
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.core.util.formatFileSize

class DuplicateGroupAdapter(
    private val onToggleSelection: (file: MediaFile) -> Unit
) : ListAdapter<DuplicateGroup, DuplicateGroupAdapter.GroupViewHolder>(GroupDiffCallback()) {

    var selectedFilePaths: Set<String> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val expandedGroups = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemDuplicateGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupViewHolder(private val binding: ItemDuplicateGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val fileAdapter = FileAdapter(
            getSelectedPaths = { selectedFilePaths },
            onToggleClick = onToggleSelection
        )

        init {
            binding.rvFiles.adapter = fileAdapter
        }

        fun bind(group: DuplicateGroup) {
            val sizeText = formatFileSize(group.fileSize)
            val countText = itemView.context.getString(R.string.duplicate_group_count, group.files.size)
            binding.headerGroup.setTitle("$sizeText - $countText")
            binding.headerGroup.setExpandCollapseContentDescriptions(R.string.cd_expand_group, R.string.cd_collapse_group)

            val isExpanded = expandedGroups.contains(group.fullHash)
            binding.headerGroup.setExpanded(isExpanded, notify = false)
            binding.rvFiles.isVisible = isExpanded

            binding.headerGroup.setOnExpandedChangeListener { expanded ->
                if (expanded) {
                    expandedGroups.add(group.fullHash)
                } else {
                    expandedGroups.remove(group.fullHash)
                }
                binding.rvFiles.isVisible = expanded
            }

            fileAdapter.submitList(group.files)
        }
    }

    private class GroupDiffCallback : DiffUtil.ItemCallback<DuplicateGroup>() {
        override fun areItemsTheSame(oldItem: DuplicateGroup, newItem: DuplicateGroup): Boolean =
            oldItem.fullHash == newItem.fullHash

        override fun areContentsTheSame(oldItem: DuplicateGroup, newItem: DuplicateGroup): Boolean =
            oldItem == newItem
    }

    private class FileAdapter(
        private val getSelectedPaths: () -> Set<String>,
        private val onToggleClick: (MediaFile) -> Unit
    ) : ListAdapter<MediaFile, FileAdapter.FileViewHolder>(FileDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val binding = ItemDuplicateFileBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return FileViewHolder(binding)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class FileViewHolder(private val binding: ItemDuplicateFileBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(file: MediaFile) {
                binding.tvFileName.text = file.name
                binding.tvFilePath.text = file.path

                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = getSelectedPaths().contains(file.path)
                binding.cbSelect.setOnClickListener {
                    onToggleClick(file)
                }
                binding.root.setOnClickListener {
                    binding.cbSelect.performClick()
                }
            }
        }

        private class FileDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
            override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem.path == newItem.path

            override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean =
                oldItem == newItem
        }
    }
}
