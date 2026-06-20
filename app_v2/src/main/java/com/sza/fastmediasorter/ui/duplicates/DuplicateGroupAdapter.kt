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
import com.sza.fastmediasorter.ui.common.dragselect.DragSelectTouchListener

private const val PAYLOAD_SELECTION = "payload_selection"

class DuplicateGroupAdapter(
    private val onToggleSelection: (file: MediaFile) -> Unit,
    // S0512: within-group drag-select unions a range of file paths into the selection.
    private val onSelectRange: (paths: List<String>) -> Unit = {}
) : ListAdapter<DuplicateGroup, DuplicateGroupAdapter.GroupViewHolder>(GroupDiffCallback()) {

    var selectedFilePaths: Set<String> = emptySet()
        set(value) {
            val old = field
            field = value
            if (old == value) return
            // Targeted refresh: only rows whose selection membership flipped are rebound,
            // avoiding a full-list notifyDataSetChanged() flicker on every drag-select tick (S0512).
            boundGroupHolders.forEach { it.refreshSelection(old, value) }
        }

    private val expandedGroups = mutableSetOf<String>()

    // Currently bound group holders, so a selection change can push a targeted refresh
    // into each nested FileAdapter instead of rebinding the whole list.
    private val boundGroupHolders = mutableSetOf<GroupViewHolder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemDuplicateGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        boundGroupHolders.add(holder)
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: GroupViewHolder) {
        super.onViewRecycled(holder)
        boundGroupHolders.remove(holder)
        holder.detachDragSelect()
    }

    inner class GroupViewHolder(private val binding: ItemDuplicateGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val fileAdapter = FileAdapter(
            getSelectedPaths = { selectedFilePaths },
            onToggleClick = onToggleSelection
        )

        // S0512: within-group drag-select. Cross-group is architecturally blocked by the nested RV,
        // so the listener is scoped to this group's inner files RecyclerView.
        private val dragSelectListener = DragSelectTouchListener(object : DragSelectTouchListener.Callback {
            // Duplicates checkboxes are permanently visible - selection is always active.
            override fun isActive(): Boolean = true

            override fun onSelectionStart(position: Int) {
                timber.log.Timber.d("S0512: Duplicates within-group drag-select start at position=$position")
                pathsInRange(position, position)?.let(onSelectRange)
            }

            override fun onSelectionRangeChanged(start: Int, end: Int) {
                pathsInRange(start, end)?.let(onSelectRange)
            }

            override fun onSelectionEnd() = Unit
        })

        init {
            binding.rvFiles.adapter = fileAdapter
            dragSelectListener.attach(binding.rvFiles)
        }

        fun detachDragSelect() {
            dragSelectListener.detach(binding.rvFiles)
        }

        fun refreshSelection(old: Set<String>, new: Set<String>) {
            fileAdapter.refreshSelection(old, new)
        }

        private fun pathsInRange(start: Int, end: Int): List<String>? {
            val files = fileAdapter.currentList
            if (files.isEmpty()) return null
            val lo = minOf(start, end).coerceAtLeast(0)
            val hi = maxOf(start, end).coerceAtMost(files.size - 1)
            if (lo > hi) return null
            return files.subList(lo, hi + 1).map { it.path }
        }

        fun bind(group: DuplicateGroup) {
            val sizeText = formatFileSize(group.fileSize)
            val countText = itemView.context.getString(R.string.duplicate_group_count, group.files.size)
            // S0535: title carries a representative file name; the size/count summary moves to the
            // unified summary slot so it stays informative when the group is collapsed.
            binding.headerGroup.setTitle(group.files.firstOrNull()?.name.orEmpty())
            binding.headerGroup.setSummary("$sizeText - $countText")
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

        override fun onBindViewHolder(holder: FileViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SELECTION }) {
                holder.bindSelection(getItem(position))
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        // Rebinds only the checkbox of files whose membership flipped between the two selections.
        fun refreshSelection(old: Set<String>, new: Set<String>) {
            val list = currentList
            for (i in list.indices) {
                val path = list[i].path
                if ((path in old) != (path in new)) {
                    notifyItemChanged(i, PAYLOAD_SELECTION)
                }
            }
        }

        inner class FileViewHolder(private val binding: ItemDuplicateFileBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(file: MediaFile) {
                binding.tvFileName.text = file.name
                binding.tvFilePath.text = file.path
                bindSelection(file)
                binding.root.setOnClickListener {
                    binding.cbSelect.performClick()
                }
            }

            fun bindSelection(file: MediaFile) {
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = getSelectedPaths().contains(file.path)
                binding.cbSelect.setOnClickListener {
                    onToggleClick(file)
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
