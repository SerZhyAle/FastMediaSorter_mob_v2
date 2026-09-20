package com.sza.fastmediasorter.ui.launcher.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemLauncherAppGridCellBinding
import com.sza.fastmediasorter.databinding.ItemLauncherAppGroupHeaderBinding
import com.sza.fastmediasorter.databinding.ItemLauncherAppGroupTileBinding
import com.sza.fastmediasorter.ui.common.widget.MediaItemThumbnailBinder
import java.io.File

/**
 * S1089: renders an installed-app grid as labeled icon cells. Kept separate from
 * [com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarIconAdapter] (icon-only, shared by the
 * taskbar strips) so adding a label here does not change those strips.
 */
class LauncherAppGridAdapter(
    private val onAppClick: (AppItem) -> Unit,
    // S0427: returns whether the app's quick actions were expanded; false leaves the cell behaving as
    // an ordinary un-long-pressable one.
    private val onAppLongClick: (View, AppItem) -> Boolean = { _, _ -> false },
    private val onGroupToggle: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /**
     * One installed app in the grid. [id] identifies the item across updates.
     *
     * S1401: the icon is a cached file, never a decoded image - a hundred live drawables is exactly the
     * cost the icon cache exists to remove. [iconVersion] is the app's last-update time and becomes the
     * image cache key, so a reinstalled app cannot keep serving the icon it had before.
     */
    data class AppItem(
        val id: String,
        val label: String,
        val iconFile: File?,
        val iconVersion: Long
    )

    private val items = mutableListOf<DisplayItem>()

    // S3246: the single Glide entry point shared with ui/browse, so this adapter no longer owns its
    // own request/clear lifecycle. Constructed rather than injected - the adapter is built by hand in
    // LauncherAllAppsFragment and the binder is stateless.
    private val thumbnailBinder = MediaItemThumbnailBinder()

    fun submitGroups(groups: List<LauncherAppGroupSection>) {
        items.clear()
        groups.forEach { group ->
            if (group.isSingleApp) {
                items += DisplayItem.App(group.apps.single())
            } else {
                items += DisplayItem.Header(group)
            }
            if (!group.isSingleApp && (group.isPreview || group.isExpanded)) {
                group.apps.forEach { items += DisplayItem.App(it) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (val item = items[position]) {
        is DisplayItem.Header -> if (item.group.isPreview) {
            VIEW_TYPE_PREVIEW_HEADER
        } else {
            VIEW_TYPE_GROUP_TILE
        }
        is DisplayItem.App -> VIEW_TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_PREVIEW_HEADER -> {
                PreviewHeaderViewHolder(ItemLauncherAppGroupHeaderBinding.inflate(inflater, parent, false))
            }

            VIEW_TYPE_GROUP_TILE -> {
                GroupTileViewHolder(ItemLauncherAppGroupTileBinding.inflate(inflater, parent, false))
            }

            else -> AppViewHolder(ItemLauncherAppGridCellBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DisplayItem.Header -> when (holder) {
                is PreviewHeaderViewHolder -> holder.bind(item.group)
                is GroupTileViewHolder -> holder.bind(item.group)
                else -> error("Unexpected holder for an app group")
            }

            is DisplayItem.App -> (holder as AppViewHolder).bind(item.app)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AppViewHolder) holder.clear()
    }

    /**
     * S2736: the three row heights the preview arithmetic needs, read off the children the grid has
     * already laid out. Measured rather than taken from a dimension resource because the app cell
     * grows with the font scale, and the margins are added back because a child's own height leaves
     * them out while the row the grid stacks does not.
     */
    data class RowHeights(val previewHeaderPx: Int, val appCellPx: Int, val letterTilePx: Int)

    fun measureRowHeights(recyclerView: RecyclerView): RowHeights {
        var header = 0
        var app = 0
        var tile = 0
        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index) ?: continue
            val height = child.height + verticalMarginsOf(child)
            when (recyclerView.getChildViewHolder(child)) {
                is PreviewHeaderViewHolder -> if (header == 0) header = height
                is AppViewHolder -> if (app == 0) app = height
                is GroupTileViewHolder -> if (tile == 0) tile = height
            }
        }
        return RowHeights(previewHeaderPx = header, appCellPx = app, letterTilePx = tile)
    }

    private fun verticalMarginsOf(child: View): Int {
        val params = child.layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
        return params.topMargin + params.bottomMargin
    }

    fun getSpanSizeLookup(spanCount: Int): GridLayoutManager.SpanSizeLookup =
        object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (getItemViewType(position) == VIEW_TYPE_PREVIEW_HEADER) spanCount else 1
        }

    inner class PreviewHeaderViewHolder(
        private val binding: ItemLauncherAppGroupHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: LauncherAppGroupSection) {
            binding.groupTitle.text = group.title
            binding.groupCount.text = if (group.isPreview) "" else group.apps.size.toString()
            binding.groupIndicator.rotation = if (group.isExpanded) EXPANDED_ROTATION else 0f
            binding.root.contentDescription = group.title.ifBlank {
                binding.root.context.getString(R.string.launcher_menu_all_apps)
            }
            binding.root.setOnClickListener { onGroupToggle(group.key) }
        }
    }

    inner class GroupTileViewHolder(
        private val binding: ItemLauncherAppGroupTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: LauncherAppGroupSection) {
            binding.groupTitle.text = group.title
            val isVowel = group.title in VOWEL_GROUPS
            binding.groupTitle.isActivated = isVowel
            val titleColor = when {
                isVowel -> com.google.android.material.R.attr.colorOnPrimaryContainer
                group.title == SYMBOL_GROUP || group.title.singleOrNull()?.let { it in 'А'..'Я' } == true -> {
                    androidx.appcompat.R.attr.colorPrimary
                }
                else -> com.google.android.material.R.attr.colorOnSurface
            }
            binding.groupTitle.setTextColor(MaterialColors.getColor(binding.groupTitle, titleColor))
            binding.groupCount.text = group.apps.size.toString()
            binding.groupIndicator.rotation = if (group.isExpanded) EXPANDED_ROTATION else 0f
            binding.root.contentDescription = group.title
            binding.root.setOnClickListener { onGroupToggle(group.key) }
        }
    }

    inner class AppViewHolder(private val binding: ItemLauncherAppGridCellBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppItem) {
            thumbnailBinder.bindIcon(
                view = binding.appIcon,
                iconFile = item.iconFile,
                versionKey = item.iconVersion,
                placeholder = R.drawable.ic_launcher_mode,
            )
            binding.appLabel.text = item.label
            binding.root.contentDescription = item.label
            binding.root.setOnClickListener { onAppClick(item) }
            binding.root.setOnLongClickListener { onAppLongClick(binding.root, item) }
        }

        /** A request left running against a recycled view would paint the wrong app's icon. */
        fun clear() {
            thumbnailBinder.clear(binding.appIcon)
        }
    }

    private sealed interface DisplayItem {
        data class Header(val group: LauncherAppGroupSection) : DisplayItem
        data class App(val app: AppItem) : DisplayItem
    }

    private companion object {
        const val VIEW_TYPE_PREVIEW_HEADER = 0
        const val VIEW_TYPE_GROUP_TILE = 1
        const val VIEW_TYPE_APP = 2
        const val EXPANDED_ROTATION = 180f
        const val SYMBOL_GROUP = "#"

        val VOWEL_GROUPS = setOf(
            "A", "E", "I", "O", "U", "Y",
            "А", "Е", "Ё", "И", "О", "У", "Ы", "Э", "Ю", "Я",
        )
    }
}
