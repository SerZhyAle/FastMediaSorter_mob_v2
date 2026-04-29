package com.sza.fastmediasorter.ui.icon.picker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.icon.ResourceIconRegistry
import com.sza.fastmediasorter.ui.icon.ResourceIconSet

/** RecyclerView adapter for the icon picker grid (S0034 Phase 06). */
class IconPickerAdapter(
    private var currentIconId: String?,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<IconPickerAdapter.IconViewHolder>() {

    private var items: List<String> = emptyList()

    /** Replace the displayed icon list when the user switches tabs. */
    fun setItems(set: ResourceIconSet) {
        items = ResourceIconRegistry.idsFor(set)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_picker, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val id = items[position]
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
            val previous = currentIconId
            currentIconId = id
            // Refresh only the two affected cells to avoid full-list flicker
            val previousPos = items.indexOf(previous)
            if (previousPos != -1) notifyItemChanged(previousPos)
            notifyItemChanged(position)
            onPick(id)
        }
    }

    override fun getItemCount() = items.size

    inner class IconViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val vSelected: View = view.findViewById(R.id.vSelected)
    }
}
