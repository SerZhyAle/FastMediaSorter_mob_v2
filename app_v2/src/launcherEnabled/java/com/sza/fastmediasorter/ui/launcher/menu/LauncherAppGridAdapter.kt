package com.sza.fastmediasorter.ui.launcher.menu

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.ItemLauncherAppGridCellBinding

/**
 * S1089: renders the Start-menu "All apps" grid as labeled icon cells. Kept separate from
 * [com.sza.fastmediasorter.ui.launcher.helpers.LauncherTaskbarIconAdapter] (icon-only, shared by the
 * taskbar strips) so adding a label here does not change those strips.
 */
class LauncherAppGridAdapter(
    private val onAppClick: (AppItem) -> Unit,
    // S0427: returns whether the app's quick actions were expanded; false leaves the cell behaving as
    // an ordinary un-long-pressable one.
    private val onAppLongClick: (View, AppItem) -> Boolean = { _, _ -> false },
) : ListAdapter<LauncherAppGridAdapter.AppItem, LauncherAppGridAdapter.VH>(DIFF) {

    /** One installed app in the grid. [id] identifies the item across updates. */
    data class AppItem(val id: String, val label: String, val icon: Drawable)

    fun submitApps(list: List<AppItem>) = submitList(list)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemLauncherAppGridCellBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemLauncherAppGridCellBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppItem) {
            binding.appIcon.setImageDrawable(item.icon)
            binding.appLabel.text = item.label
            binding.root.contentDescription = item.label
            binding.root.setOnClickListener { onAppClick(item) }
            binding.root.setOnLongClickListener { onAppLongClick(binding.root, item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppItem>() {
            override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean =
                oldItem.id == newItem.id

            // icon is excluded: PackageManager returns a fresh Drawable per call and Drawable uses
            // identity equality, so comparing it would rebind every visible cell on every update.
            override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean =
                oldItem.label == newItem.label
        }
    }
}
