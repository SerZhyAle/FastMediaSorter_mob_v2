package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemLauncherTaskbarAddBinding
import com.sza.fastmediasorter.databinding.ItemLauncherTaskbarIconBinding

/** One icon in a taskbar strip. [id] is what makes two entries the same item across updates. */
data class LauncherTaskbarIcon(
    val id: String,
    val label: String,
    val iconRes: Int?,
    val iconDrawable: Drawable?,
    /** The pin slot this icon occupies; -1 for a recents entry, which cannot be unpinned. */
    val position: Int = -1,
)

/** A row in a taskbar strip: a real icon, or the edit-mode trailing "+" that pins one more app. */
sealed interface LauncherTaskbarRow {
    data class Icon(val icon: LauncherTaskbarIcon, val editing: Boolean) : LauncherTaskbarRow
    data object Add : LauncherTaskbarRow
}

/**
 * S0404: renders a horizontal taskbar strip (recents or pinned). Visuals arrive pre-resolved.
 *
 * Only the pinned strip turns editing on ([setEditMode]); recents never does. In edit mode each icon
 * shows an unpin "X" and a trailing "+" is appended. Both are inert on the recents adapter, which is
 * built with [onIconClick] alone - [onRemoveClick]/[onAddClick] default to no-ops.
 */
class LauncherTaskbarIconAdapter(
    private val onIconClick: (LauncherTaskbarIcon) -> Unit,
    private val onRemoveClick: (LauncherTaskbarIcon) -> Unit = {},
    private val onAddClick: () -> Unit = {},
) : ListAdapter<LauncherTaskbarRow, RecyclerView.ViewHolder>(DIFF) {

    private var icons: List<LauncherTaskbarIcon> = emptyList()
    private var editMode: Boolean = false

    /** Replaces the strip's icons, preserving the current edit state. */
    fun submitIcons(list: List<LauncherTaskbarIcon>) {
        icons = list
        rebuild()
    }

    /** Toggles the unpin badges and the trailing "+"; a no-op change is skipped. */
    fun setEditMode(on: Boolean) {
        if (editMode == on) return
        editMode = on
        rebuild()
    }

    private fun rebuild() {
        val rows = icons.map { LauncherTaskbarRow.Icon(it, editMode) }
        submitList(if (editMode) rows + LauncherTaskbarRow.Add else rows)
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is LauncherTaskbarRow.Add) VIEW_ADD else VIEW_ICON

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_ADD) {
            AddViewHolder(ItemLauncherTaskbarAddBinding.inflate(inflater, parent, false))
        } else {
            IconViewHolder(ItemLauncherTaskbarIconBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is LauncherTaskbarRow.Icon -> (holder as IconViewHolder).bind(row)
            LauncherTaskbarRow.Add -> (holder as AddViewHolder).bind()
        }
    }

    inner class IconViewHolder(
        private val binding: ItemLauncherTaskbarIconBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: LauncherTaskbarRow.Icon) {
            val item = row.icon
            if (item.iconDrawable != null) {
                binding.taskbarIcon.setImageDrawable(item.iconDrawable)
            } else if (item.iconRes != null) {
                binding.taskbarIcon.setImageResource(item.iconRes)
            }
            // The strip draws no text, so the label has to reach both a screen reader and a
            // long-press tooltip or the icon is unidentifiable.
            binding.root.contentDescription = item.label
            TooltipCompat.setTooltipText(binding.root, item.label)
            bindEditControls(row)
        }

        private fun bindEditControls(row: LauncherTaskbarRow.Icon) {
            val item = row.icon
            binding.taskbarUnpinBadge.isVisible = row.editing
            if (row.editing) {
                // In edit mode the body arranges, it does not launch - only the badge acts.
                binding.root.setOnClickListener(null)
                binding.root.isClickable = false
                binding.taskbarUnpinBadge.contentDescription =
                    binding.root.context.getString(R.string.launcher_edit_unpin_named, item.label)
                binding.taskbarUnpinBadge.setOnClickListener { onRemoveClick(item) }
            } else {
                binding.root.isClickable = true
                binding.root.setOnClickListener { onIconClick(item) }
            }
        }
    }

    inner class AddViewHolder(
        private val binding: ItemLauncherTaskbarAddBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.contentDescription =
                binding.root.context.getString(R.string.launcher_edit_pin_add)
            binding.root.setOnClickListener { onAddClick() }
        }
    }

    private companion object {
        const val VIEW_ICON = 0
        const val VIEW_ADD = 1

        val DIFF = object : DiffUtil.ItemCallback<LauncherTaskbarRow>() {
            override fun areItemsTheSame(oldItem: LauncherTaskbarRow, newItem: LauncherTaskbarRow): Boolean =
                when {
                    oldItem is LauncherTaskbarRow.Icon && newItem is LauncherTaskbarRow.Icon ->
                        oldItem.icon.id == newItem.icon.id
                    oldItem is LauncherTaskbarRow.Add && newItem is LauncherTaskbarRow.Add -> true
                    else -> false
                }

            // iconDrawable is excluded: PackageManager returns a fresh instance per call and
            // Drawable uses identity equality, so comparing it would rebind the strip every time.
            override fun areContentsTheSame(oldItem: LauncherTaskbarRow, newItem: LauncherTaskbarRow): Boolean =
                when {
                    oldItem is LauncherTaskbarRow.Icon && newItem is LauncherTaskbarRow.Icon ->
                        oldItem.editing == newItem.editing &&
                            oldItem.icon.id == newItem.icon.id &&
                            oldItem.icon.label == newItem.icon.label &&
                            oldItem.icon.iconRes == newItem.icon.iconRes &&
                            oldItem.icon.position == newItem.icon.position
                    else -> true
                }
        }
    }
}
