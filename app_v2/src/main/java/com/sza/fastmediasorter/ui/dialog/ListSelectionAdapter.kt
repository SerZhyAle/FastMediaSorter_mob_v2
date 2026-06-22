package com.sza.fastmediasorter.ui.dialog

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R

/**
 * RecyclerView adapter rendering a selectable list of items via `item_list_selection.xml`
 * styled by the project button taxonomy (S0567, ADR-3). No runtime-built buttons, no
 * hardcoded colors - the selected item is marked with a leading check icon.
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

    inner class ViewHolder(val button: MaterialButton) : RecyclerView.ViewHolder(button)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_selection, parent, false) as MaterialButton
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.button.text = formatter.getDisplayName(item)
        holder.button.icon = if (isSelected(item)) {
            androidx.core.content.ContextCompat.getDrawable(holder.button.context, R.drawable.ic_check)
        } else {
            formatter.getIcon(item)
        }
        holder.button.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
