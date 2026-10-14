package com.sza.fastmediasorter.ui.systeminfo.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemSystemInfoEmptyBinding
import com.sza.fastmediasorter.databinding.ItemSystemInfoFieldBinding
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.systeminfo.model.SystemInfoListItem

class SystemInfoAdapter(
    private val onToggleSection: (String) -> Unit,
    private val onCopySection: (String) -> Unit,
    private val onCopyField: (String, String) -> Unit,
) : ListAdapter<SystemInfoListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SystemInfoListItem.SectionHeader -> TYPE_HEADER
        is SystemInfoListItem.Field -> TYPE_FIELD
        is SystemInfoListItem.EmptyState -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                header = CollapsibleSectionHeader(parent.context).apply {
                    val margin = resources.getDimensionPixelSize(R.dimen.margin_tiny)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setMargins(margin, margin, margin, margin)
                    }
                },
                onToggleSection = onToggleSection,
                onCopySection = onCopySection,
            )
            TYPE_FIELD -> FieldViewHolder(
                binding = ItemSystemInfoFieldBinding.inflate(inflater, parent, false),
                onCopyField = onCopyField,
            )
            else -> EmptyViewHolder(
                binding = ItemSystemInfoEmptyBinding.inflate(inflater, parent, false),
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SystemInfoListItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is SystemInfoListItem.Field -> (holder as FieldViewHolder).bind(item)
            is SystemInfoListItem.EmptyState -> (holder as EmptyViewHolder).bind(item)
        }
    }

    private class HeaderViewHolder(
        private val header: CollapsibleSectionHeader,
        private val onToggleSection: (String) -> Unit,
        private val onCopySection: (String) -> Unit,
    ) : RecyclerView.ViewHolder(header) {

        private val copyButton: ImageButton = ImageButton(header.context).apply {
            val size = header.resources.getDimensionPixelSize(R.dimen.touch_target_min_height)
            layoutParams = ViewGroup.LayoutParams(size, size)
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_copy))
            background = AppCompatResources.getDrawable(context, R.drawable.focus_button_background)
            contentDescription = context.getString(R.string.sysinfo_action_copy_section)
            isFocusable = true
            isClickable = true
        }

        init {
            header.setTrailingControl(copyButton)
        }

        fun bind(item: SystemInfoListItem.SectionHeader) {
            header.setTitle(item.title)
            header.setSummary("(${item.count})")
            header.setExpanded(item.isExpanded, notify = false)
            header.setOnExpandedChangeListener {
                onToggleSection(item.title)
            }
            copyButton.setOnClickListener {
                onCopySection(item.title)
            }
        }
    }

    private class FieldViewHolder(
        private val binding: ItemSystemInfoFieldBinding,
        private val onCopyField: (String, String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SystemInfoListItem.Field) {
            val labelText = if (item.label.endsWith(":")) item.label else "${item.label}:"
            binding.tvFieldLabel.text = labelText
            binding.tvFieldValue.text = item.value

            binding.root.setOnClickListener {
                onCopyField(item.label, item.value)
            }
        }
    }

    private class EmptyViewHolder(
        private val binding: ItemSystemInfoEmptyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SystemInfoListItem.EmptyState) {
            binding.tvEmptyMessage.text = item.message
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FIELD = 1
        private const val TYPE_EMPTY = 2

        private val DIFF = object : DiffUtil.ItemCallback<SystemInfoListItem>() {
            override fun areItemsTheSame(
                oldItem: SystemInfoListItem,
                newItem: SystemInfoListItem,
            ): Boolean = when {
                oldItem is SystemInfoListItem.SectionHeader && newItem is SystemInfoListItem.SectionHeader ->
                    oldItem.title == newItem.title
                oldItem is SystemInfoListItem.Field && newItem is SystemInfoListItem.Field ->
                    oldItem.sectionTitle == newItem.sectionTitle && oldItem.label == newItem.label
                oldItem is SystemInfoListItem.EmptyState && newItem is SystemInfoListItem.EmptyState ->
                    oldItem.message == newItem.message
                else -> false
            }

            override fun areContentsTheSame(
                oldItem: SystemInfoListItem,
                newItem: SystemInfoListItem,
            ): Boolean = oldItem == newItem
        }
    }
}
