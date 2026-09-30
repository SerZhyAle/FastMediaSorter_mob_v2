package com.sza.fastmediasorter.ui.icon.picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.icon.ResourceIconRegistry
import com.sza.fastmediasorter.ui.icon.ResourceIconSet

/** RecyclerView adapter for the icon picker grid (S0034 Phase 06). */
class IconPickerAdapter(
    private var currentIconId: String?,
    private val onPick: (String) -> Unit
) : ListAdapter<String, IconPickerAdapter.IconViewHolder>(IconDiffCallback) {

    /** Replace the displayed icon list when the user switches tabs. */
    fun setItems(set: ResourceIconSet) {
        submitList(ResourceIconRegistry.idsFor(set))
        timber.log.Timber.d("S3784: iconPicker tab diff")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_picker, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val id = getItem(position)
        // Resolve drawable; fall back to transparent placeholder when registry misses
        val drawableRes = ResourceIconRegistry.resolveDrawable(id)
        if (drawableRes != null) {
            holder.ivIcon.setImageResource(drawableRes)
        } else {
            holder.ivIcon.setImageDrawable(null)
        }
        // Show selection ring only for the currently-active icon id
        holder.vSelected.isVisible = id == currentIconId
        holder.itemView.setOnClickListener {
            val clickedPos = holder.bindingAdapterPosition
            if (clickedPos == RecyclerView.NO_POSITION) return@setOnClickListener
            val previous = currentIconId
            currentIconId = id
            // Refresh only the two affected cells to avoid full-list flicker
            val previousPos = currentList.indexOf(previous)
            if (previousPos != -1) notifyItemChanged(previousPos)
            notifyItemChanged(clickedPos)
            onPick(id)
        }
    }

    inner class IconViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val vSelected: View = view.findViewById(R.id.vSelected)
    }
}

private object IconDiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}
