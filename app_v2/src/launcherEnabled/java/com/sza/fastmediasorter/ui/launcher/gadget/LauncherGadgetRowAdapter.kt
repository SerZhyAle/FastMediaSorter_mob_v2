package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.databinding.ItemLauncherGadgetRowBinding

/**
 * One row in a list gadget. [id] is the row's identity; [bitmap] is an already-decoded favicon tile
 * (streams) and wins over [iconRes] (tracks) when present.
 *
 * [tintIcon] is per-row, not a layout attribute, because the leading slot holds three different kinds
 * of image and only one of them may be tinted: a favicon bitmap would be repainted a flat colour, and
 * ic_music_note is a gold gradient a tint would destroy - but ic_cast fills white and is invisible on
 * the card without one.
 */
data class LauncherGadgetRow(
    val id: String,
    val title: String,
    @DrawableRes val iconRes: Int?,
    val bitmap: Bitmap? = null,
    val tintIcon: Boolean = false,
)

/** S0404: shared row rendering for the playlist and streams gadgets - same shape, same behaviour. */
class LauncherGadgetRowAdapter(
    private val onRowClick: (LauncherGadgetRow) -> Unit,
) : ListAdapter<LauncherGadgetRow, LauncherGadgetRowAdapter.RowViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(
            ItemLauncherGadgetRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RowViewHolder(
        private val binding: ItemLauncherGadgetRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LauncherGadgetRow) {
            binding.gadgetRowIcon.imageTintList = if (item.tintIcon) {
                ColorStateList.valueOf(
                    MaterialColors.getColor(
                        binding.gadgetRowIcon,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                    )
                )
            } else {
                // Must be cleared, not left: the holder is recycled, so a tint set for a previous row
                // would repaint this row's favicon or flatten its gradient icon.
                null
            }
            when {
                item.bitmap != null -> binding.gadgetRowIcon.setImageBitmap(item.bitmap)
                item.iconRes != null -> binding.gadgetRowIcon.setImageResource(item.iconRes)
                else -> binding.gadgetRowIcon.setImageDrawable(null)
            }
            binding.gadgetRowTitle.text = item.title
            binding.root.contentDescription = item.title
            binding.root.setOnClickListener { onRowClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<LauncherGadgetRow>() {
            override fun areItemsTheSame(oldItem: LauncherGadgetRow, newItem: LauncherGadgetRow): Boolean =
                oldItem.id == newItem.id

            // Bitmap is compared by identity on purpose: a re-decoded tile is a different instance for
            // the same channel, and comparing it would rebind the whole list on every reload.
            override fun areContentsTheSame(oldItem: LauncherGadgetRow, newItem: LauncherGadgetRow): Boolean =
                oldItem.id == newItem.id &&
                    oldItem.title == newItem.title &&
                    oldItem.iconRes == newItem.iconRes
        }
    }
}
