package com.sza.fastmediasorter.ui.dialog

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R

/**
 * RecyclerView adapter rendering a selectable list of items via `item_list_selection.xml`.
 *
 * S0787: each item is a readable full-width row (not an outlined "oval" button). The selected item is
 * highlighted by activating the row background AND a trailing check icon - both, so the current value
 * is obvious with or without colour. An optional leading icon comes from the formatter. No hardcoded
 * colors - theme attributes only.
 *
 * @see ListSelectionDialog
 */
class ListSelectionAdapter<T>(
    private val items: List<T>,
    private val formatter: ItemFormatter<T>,
    private val isSelected: (T) -> Boolean,
    private val onClick: (T) -> Unit,
) : RecyclerView.Adapter<ListSelectionAdapter<T>.ViewHolder>() {

    /**
     * Maps a domain item to its display name and optional leading icon.
     */
    interface ItemFormatter<T> {
        fun getDisplayName(item: T): String
        fun getIcon(item: T): Drawable? = null
    }

    inner class ViewHolder(val row: View) : RecyclerView.ViewHolder(row) {
        val label: TextView = row.findViewById(R.id.item_label)
        val icon: ImageView = row.findViewById(R.id.item_icon)
        val check: ImageView = row.findViewById(R.id.item_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = formatter.getDisplayName(item)
        val selected = isSelected(item)
        // isActivated drives both the highlighted row background and the on-highlight label colour.
        holder.row.isActivated = selected
        holder.check.visibility = if (selected) View.VISIBLE else View.GONE
        val leadingIcon = formatter.getIcon(item)
        holder.icon.setImageDrawable(leadingIcon)
        holder.icon.visibility = if (leadingIcon != null) View.VISIBLE else View.GONE
        holder.row.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
