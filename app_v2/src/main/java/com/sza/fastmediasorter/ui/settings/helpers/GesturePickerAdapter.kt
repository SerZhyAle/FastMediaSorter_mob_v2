package com.sza.fastmediasorter.ui.settings.helpers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R

/**
 * S1038: grouped gesture-action picker adapter mirroring [com.sza.fastmediasorter.ui.settings.fragments.PermissionRowAdapter]
 * (sectioned Header/Entry, per-entry description). Two view types; the row bound to [selectedKey]
 * activates its highlighted background + trailing check, matching the flat picker's selection cue.
 * Disabled entries render dimmed and non-clickable so unavailable actions read as present-but-inactive.
 *
 * S2256: the action type is the host's, not one surface's enum - selection is key equality either way.
 */
class GesturePickerAdapter<T : Any>(
    private val rows: List<GesturePickerRow<T>>,
    private val selectedKey: T?,
    private val onClick: (T) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
        private const val ALPHA_DISABLED = 0.4f
        private const val ALPHA_ENABLED = 1.0f
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is GesturePickerRow.Header -> TYPE_HEADER
        is GesturePickerRow.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_gesture_picker_header, parent, false))
        } else {
            EntryViewHolder(inflater.inflate(R.layout.item_gesture_picker_entry, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is GesturePickerRow.Header -> (holder as HeaderViewHolder).bind(row)
            is GesturePickerRow.Entry -> (holder as EntryViewHolder).bind(row, selectedKey, onClick)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(header: GesturePickerRow.Header) {
            itemView.findViewById<TextView>(R.id.tv_gesture_group_title)?.setText(header.titleRes)
        }
    }

    class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun <T : Any> bind(
            entry: GesturePickerRow.Entry<T>,
            selectedKey: T?,
            onClick: (T) -> Unit,
        ) {
            val ctx = itemView.context
            itemView.findViewById<TextView>(R.id.tv_gesture_entry_title)?.setText(entry.labelRes)
            itemView.findViewById<TextView>(R.id.tv_gesture_entry_desc)?.text =
                ctx.getString(entry.explanationRes)
            itemView.findViewById<ImageView>(R.id.iv_gesture_entry_icon)
                ?.setImageResource(entry.iconRes)
            val selected = entry.actionKey == selectedKey
            itemView.isActivated = selected
            itemView.findViewById<ImageView>(R.id.item_check)?.visibility =
                if (selected) View.VISIBLE else View.GONE
            itemView.alpha = if (entry.enabled) ALPHA_ENABLED else ALPHA_DISABLED
            itemView.isEnabled = entry.enabled
            itemView.isFocusable = entry.enabled
            itemView.isClickable = entry.enabled
            itemView.setOnClickListener(
                if (entry.enabled) View.OnClickListener { onClick(entry.actionKey) } else null,
            )
        }
    }
}
