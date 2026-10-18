package com.sza.fastmediasorter.ui.systeminfo.adapter

import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemSystemInfoEmptyBinding
import com.sza.fastmediasorter.databinding.ItemSystemInfoFieldBinding
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.systeminfo.model.SystemInfoListItem
import com.google.android.material.R as MaterialR

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
            // Full touch height, narrower than tall: the width comes out of the title column.
            layoutParams = ViewGroup.LayoutParams(
                header.resources.getDimensionPixelSize(R.dimen.system_info_group_copy_button_width),
                header.resources.getDimensionPixelSize(R.dimen.touch_target_min_height),
            )
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_copy))
            background = AppCompatResources.getDrawable(context, R.drawable.focus_button_background)
            contentDescription = context.getString(R.string.sysinfo_action_copy_section)
            isFocusable = true
            isClickable = true
        }

        init {
            header.setTrailingControl(copyButton)
            header.setHeaderPadding(
                header.resources.getDimensionPixelSize(R.dimen.system_info_group_padding_horizontal),
                header.resources.getDimensionPixelSize(R.dimen.system_info_group_padding_vertical),
            )
            header.setLeadingSpacing(header.resources.getDimensionPixelSize(R.dimen.system_info_group_leading_spacing))
            header.setTitleTextSizePx(header.resources.getDimension(R.dimen.system_info_group_title_text_size))
            header.setTitleBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
        }

        fun bind(item: SystemInfoListItem.SectionHeader) {
            header.setTitle(titleWithCount(item))
            // The count rides in the title line; a summary line below it would only make the tile taller.
            header.setSummary(null)
            val iconRes = item.iconRes
            if (iconRes != null) header.setIcon(iconRes) else header.setIcon(null)
            header.setExpanded(item.isExpanded, notify = false)
            header.setOnExpandedChangeListener {
                onToggleSection(item.stateKey)
            }
            copyButton.setOnClickListener {
                onCopySection(item.stateKey)
            }
        }

        private fun titleWithCount(item: SystemInfoListItem.SectionHeader): CharSequence {
            val countColor = MaterialColors.getColor(header, MaterialR.attr.colorOnSurfaceVariant)
            return SpannableStringBuilder(item.title).apply {
                // A plain space, not a no-break one: in a narrow tile "Title (12)" can be wider than the column,
                // and a no-break space would then split the count itself instead of moving it to the next line.
                append(' ')
                val countStart = length
                append("(${item.count})")
                setSpan(RelativeSizeSpan(COUNT_RELATIVE_SIZE), countStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(countColor), countStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private companion object {
            const val COUNT_RELATIVE_SIZE = 0.8f
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
            binding.viewRowShade.isVisible = item.isShaded

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
                    oldItem.stateKey == newItem.stateKey
                oldItem is SystemInfoListItem.Field && newItem is SystemInfoListItem.Field ->
                    oldItem.sectionKey == newItem.sectionKey && oldItem.label == newItem.label
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
