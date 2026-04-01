package com.sza.fastmediasorter.ui.duplicates

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            binding.tvGroupSize.text = formatFileSize(group.fileSize)
            binding.tvGroupCount.text = itemView.context.getString(R.string.duplicate_group_count, group.files.size)

            val isExpanded = expandedGroups.contains(group.fullHash)
            binding.rvFiles.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.ivExpand.rotation = if (isExpanded) 180f else 0f

            val cdStringRes = if (isExpanded) R.string.cd_collapse_group else R.string.cd_expand_group
            binding.ivExpand.contentDescription = itemView.context.getString(cdStringRes)

            binding.root.setOnClickListener {
                if (isExpanded) {
                    expandedGroups.remove(group.fullHash)
                } else {
                    expandedGroups.add(group.fullHash)
                }
                notifyItemChanged(bindingAdapterPosition)
            }
            binding.ivExpand.setOnClickListener { binding.root.performClick() }

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
