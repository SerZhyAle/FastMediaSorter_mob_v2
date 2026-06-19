package com.sza.fastmediasorter.ui.keybinding

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemKeybindingRowBinding
import com.sza.fastmediasorter.domain.input.CommandGroup
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.keybinding.helpers.KeybindingRowLabelFormatter
import com.sza.fastmediasorter.ui.keybinding.helpers.remappableDevices

sealed class KeybindingListItem {
    data class Header(val group: CommandGroup, val isExpanded: Boolean) : KeybindingListItem()
    data class Row(val row: KeybindingRow) : KeybindingListItem()
}

class KeybindingListAdapter(
    private val formatter: KeybindingRowLabelFormatter,
    private val onRemapClick: (commandId: String, device: String, slot: Int) -> Unit,
    private val onResetClick: (commandId: String) -> Unit,
    private val onGroupHeaderClick: (group: CommandGroup) -> Unit,
    private val onGroupResetClick: (group: CommandGroup) -> Unit
) : ListAdapter<KeybindingListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ROW = 1
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is KeybindingListItem.Header -> VIEW_TYPE_HEADER
        is KeybindingListItem.Row -> VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(createHeaderView(parent))
            VIEW_TYPE_ROW -> {
                val binding = ItemKeybindingRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                RowViewHolder(binding)
            }
            else -> throw IllegalStateException("Unknown view type: $viewType")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is KeybindingListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is KeybindingListItem.Row -> (holder as RowViewHolder).bind(item.row)
        }
    }

    // ----- ViewHolders -----

    inner class HeaderViewHolder(private val header: CollapsibleSectionHeader) : RecyclerView.ViewHolder(header) {
        private val resetBtn: ImageView = header.tag as ImageView

        fun bind(item: KeybindingListItem.Header) {
            val groupResName = "keybinding_group_" + item.group.name.lowercase()
            val groupLabel = formatter.resolveGroupLabel(groupResName)
            header.setTitle(groupLabel)
            header.setExpanded(item.isExpanded, notify = false)
            header.setOnExpandedChangeListener { _ -> onGroupHeaderClick(item.group) }
            resetBtn.setOnClickListener { onGroupResetClick(item.group) }
        }
    }

    inner class RowViewHolder(private val binding: ItemKeybindingRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: KeybindingRow) {
            binding.tvCommandLabel.text = row.labelKey
            binding.tvBindings.text = formatBindings(row.bindings)

            binding.btnRemap.setOnClickListener { anchor ->
                // Let the user pick which device slot to remap (S0509): the row used to remap only
                // the first existing device, so a gamepad binding was visible but never editable.
                val devices = remappableDevices(row.bindings)
                val popup = PopupMenu(anchor.context, anchor)
                devices.forEachIndexed { index, device ->
                    val current = row.bindings[device]?.joinToString(", ") { formatter.format(it) }
                        ?: anchor.context.getString(R.string.keybinding_device_unset)
                    popup.menu.add(0, index, index, "${formatter.deviceLabel(device)}: $current")
                }
                popup.setOnMenuItemClickListener { item ->
                    onRemapClick(row.commandId, devices[item.itemId], 0)
                    true
                }
                popup.show()
            }

            binding.btnReset.isEnabled = row.hasOverride
            binding.btnReset.alpha = if (row.hasOverride) 1f else 0.3f
            binding.btnReset.setOnClickListener {
                onResetClick(row.commandId)
            }
        }

        private fun formatBindings(bindings: Map<String, List<InputTrigger>>): String {
            if (bindings.isEmpty()) return itemView.context.getString(R.string.keybinding_row_no_binding)
            return bindings.entries.joinToString(" | ") { (device, triggers) ->
                val deviceIcon = when (device) {
                    "keyboard" -> "⌨"
                    "gamepad" -> "🎮"
                    "mouse" -> "🖱"
                    "vr" -> "VR"
                    else -> device
                }
                val triggerLabels = triggers.joinToString(", ") { formatter.format(it) }
                "$deviceIcon $triggerLabels"
            }
        }
    }

    // ----- helpers -----

    private fun createHeaderView(parent: ViewGroup): CollapsibleSectionHeader {
        val ctx = parent.context
        val dp = (ctx.resources.displayMetrics.density * 12).toInt()

        val resetIcon = ImageView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(dp * 3, dp * 3)
            setImageResource(R.drawable.ic_refresh)
            val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            background = ctx.obtainStyledAttributes(attrs).run { getDrawable(0).also { recycle() } }
            isClickable = true
            isFocusable = true
            contentDescription = ctx.getString(R.string.keybinding_row_reset)
        }

        return CollapsibleSectionHeader(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setTrailingControl(resetIcon)
            tag = resetIcon
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<KeybindingListItem>() {
        override fun areItemsTheSame(a: KeybindingListItem, b: KeybindingListItem) = when {
            a is KeybindingListItem.Header && b is KeybindingListItem.Header -> a.group == b.group
            a is KeybindingListItem.Row && b is KeybindingListItem.Row -> a.row.commandId == b.row.commandId
            else -> false
        }

        override fun areContentsTheSame(a: KeybindingListItem, b: KeybindingListItem) = a == b
    }
}
