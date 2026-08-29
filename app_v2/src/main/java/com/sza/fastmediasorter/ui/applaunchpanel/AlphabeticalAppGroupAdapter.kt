package com.sza.fastmediasorter.ui.applaunchpanel

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemAppAlphabeticalGroupHeaderBinding
import com.sza.fastmediasorter.databinding.ItemBorderlessAppTileBinding
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp

sealed class DisplayItem {
    data class GroupHeader(
        val section: AppGroupSection,
    ) : DisplayItem()

    data class AppTile(
        val app: InstalledApp,
        val sectionKey: String,
    ) : DisplayItem()
}

class AlphabeticalAppGroupAdapter(
    private val onAppClick: (InstalledApp) -> Unit,
    private val onGroupToggle: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<DisplayItem>()

    fun submitSections(sections: List<AppGroupSection>) {
        items.clear()
        for (section in sections) {
            if (!section.isAllGroup) {
                items.add(DisplayItem.GroupHeader(section))
            }
            if (section.isExpanded) {
                for (app in section.apps) {
                    items.add(DisplayItem.AppTile(app, section.key))
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is DisplayItem.GroupHeader -> VIEW_TYPE_HEADER
        is DisplayItem.AppTile -> VIEW_TYPE_APP_TILE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemAppAlphabeticalGroupHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemBorderlessAppTileBinding.inflate(inflater, parent, false)
                AppTileViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DisplayItem.GroupHeader -> (holder as HeaderViewHolder).bind(item.section)
            is DisplayItem.AppTile -> (holder as AppTileViewHolder).bind(item.app)
        }
    }

    override fun getItemCount(): Int = items.size

    fun getSpanSizeLookup(spanCount: Int): GridLayoutManager.SpanSizeLookup {
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (getItemViewType(position) == VIEW_TYPE_HEADER) spanCount else 1
            }
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemAppAlphabeticalGroupHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: AppGroupSection) {
            binding.tvGroupTitle.text = section.title
            binding.tvGroupCount.text = section.apps.size.toString()
            binding.ivGroupExpandIndicator.rotation = if (section.isExpanded) EXPANDED_ROTATION_DEGREES else 0f
            binding.root.setOnClickListener {
                onGroupToggle(section.key)
            }
        }
    }

    inner class AppTileViewHolder(
        private val binding: ItemBorderlessAppTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: InstalledApp) {
            binding.tvAppLabel.text = app.label
            com.bumptech.glide.Glide.with(binding.ivAppIcon).load(app.iconFile).into(binding.ivAppIcon)
            binding.root.setOnClickListener {
                onAppClick(app)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_APP_TILE = 1
        private const val EXPANDED_ROTATION_DEGREES = 180f
    }
}
