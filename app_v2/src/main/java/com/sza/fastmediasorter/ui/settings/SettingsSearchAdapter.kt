package com.sza.fastmediasorter.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemSettingsSearchResultBinding

class SettingsSearchAdapter(
    private val onItemClicked: (SettingsSearchIndex) -> Unit
) : ListAdapter<SettingsSearchIndex, SettingsSearchAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSettingsSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSettingsSearchResultBinding,
        private val onItemClicked: (SettingsSearchIndex) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SettingsSearchIndex) {
            binding.titleText.text = item.title
            binding.sectionText.text = binding.root.context.getString(
                R.string.settings_search_section_format,
                formatSection(item.sectionId)
            )
            binding.descriptionText.text = item.keywords.joinToString(separator = ", ")

            binding.root.setOnClickListener {
                onItemClicked(item)
            }
        }

        private fun formatSection(sectionId: String): String {
            return when (sectionId) {
                "general" -> binding.root.context.getString(R.string.settings_tab_general)
                "playback" -> binding.root.context.getString(R.string.settings_tab_playback)
                "destinations" -> binding.root.context.getString(R.string.settings_tab_operations)
                "images" -> binding.root.context.getString(R.string.settings_category_images)
                "video" -> binding.root.context.getString(R.string.settings_category_video)
                "audio" -> binding.root.context.getString(R.string.settings_category_audio)
                "documents" -> binding.root.context.getString(R.string.settings_category_documents)
                else -> binding.root.context.getString(R.string.settings_category_other)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SettingsSearchIndex>() {
        override fun areItemsTheSame(oldItem: SettingsSearchIndex, newItem: SettingsSearchIndex): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(oldItem: SettingsSearchIndex, newItem: SettingsSearchIndex): Boolean {
            return oldItem == newItem
        }
    }
}