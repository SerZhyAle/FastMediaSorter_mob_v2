package com.sza.fastmediasorter.ui.launcher.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemLauncherAppGridCellBinding
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
) : ListAdapter<LauncherAppGridAdapter.AppItem, LauncherAppGridAdapter.VH>(DIFF) {

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

    fun submitApps(list: List<AppItem>) = submitList(list)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemLauncherAppGridCellBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    inner class VH(private val binding: ItemLauncherAppGridCellBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppItem) {
            Glide.with(binding.appIcon)
                .load(item.iconFile)
                .signature(ObjectKey(item.iconVersion))
                .placeholder(R.drawable.ic_launcher_mode)
                .into(binding.appIcon)
            binding.appLabel.text = item.label
            binding.root.contentDescription = item.label
            binding.root.setOnClickListener { onAppClick(item) }
            binding.root.setOnLongClickListener { onAppLongClick(binding.root, item) }
        }

        /** A request left running against a recycled view would paint the wrong app's icon. */
        fun clear() {
            Glide.with(binding.appIcon).clear(binding.appIcon)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppItem>() {
            override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean =
                oldItem.label == newItem.label &&
                    oldItem.iconFile == newItem.iconFile &&
                    oldItem.iconVersion == newItem.iconVersion
        }
    }
}
