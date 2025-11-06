package com.sza.fastmediasorter_v2.ui.browse

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter_v2.databinding.ItemMediaFileBinding
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaType
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

class MediaFileAdapter(
    private val onFileClick: (MediaFile) -> Unit,
    private val onFileLongClick: (MediaFile) -> Unit,
    private val onSelectionChanged: (MediaFile, Boolean) -> Unit,
    private val onPlayClick: (MediaFile) -> Unit
) : ListAdapter<MediaFile, MediaFileAdapter.ViewHolder>(MediaFileDiffCallback()) {

    private var selectedPaths = setOf<String>()

    fun setSelectedPaths(paths: Set<String>) {
        val oldSelected = selectedPaths
        selectedPaths = paths
        
        currentList.forEachIndexed { index, file ->
            if (file.path in oldSelected || file.path in paths) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), selectedPaths)
    }

    inner class ViewHolder(
        private val binding: ItemMediaFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: MediaFile, selectedPaths: Set<String>) {
            binding.apply {
                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = file.path in selectedPaths
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(file, isChecked)
                }
                
                tvFileName.text = file.name
                tvFileInfo.text = buildFileInfo(file)
                
                // Иконка по типу файла
                ivThumbnail.setImageResource(
                    when (file.type) {
                        MediaType.IMAGE -> android.R.drawable.ic_menu_gallery
                        MediaType.VIDEO -> android.R.drawable.ic_menu_slideshow
                        MediaType.AUDIO -> android.R.drawable.ic_lock_silent_mode_off
                        MediaType.GIF -> android.R.drawable.ic_menu_gallery
                    }
                )
                
                ivThumbnail.setOnClickListener {
                    onFileClick(file)
                }
                
                root.setOnClickListener {
                    onFileClick(file)
                }
                
                root.setOnLongClickListener {
                    onFileLongClick(file)
                    true
                }
                
                btnPlay.setOnClickListener {
                    onPlayClick(file)
                }
            }
        }
        
        private fun buildFileInfo(file: MediaFile): String {
            val size = formatFileSize(file.size)
            val date = DateFormat.format("yyyy-MM-dd", Date(file.createdDate))
            return "$size • $date"
        }
        
        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (ln(size.toDouble()) / ln(1024.0)).toInt()
            return String.format(
                "%.1f %s",
                size / 1024.0.pow(digitGroups.toDouble()),
                units[digitGroups]
            )
        }
    }

    private class MediaFileDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
        override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
            return oldItem == newItem
        }
    }
}
