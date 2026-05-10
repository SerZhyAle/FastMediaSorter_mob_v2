package com.sza.fastmediasorter.ui.settings.fragments

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader
import com.sza.fastmediasorter.domain.model.PermissionStatus

sealed class PermissionRow {
    data class Header(val header: PermissionGroupHeader) : PermissionRow()
    data class Entry(val entry: PermissionEntry, val status: PermissionStatus) : PermissionRow()
}

class PermissionRowAdapter(
    private val onActionClick: (PermissionEntry, PermissionStatus) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<PermissionRow> = emptyList()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    fun refresh(newRows: List<PermissionRow>) {
        rows = newRows
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is PermissionRow.Header -> TYPE_HEADER
        is PermissionRow.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_permission_group_header, parent, false))
            else -> EntryViewHolder(inflater.inflate(R.layout.item_permission_entry, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PermissionRow.Header -> (holder as HeaderViewHolder).bind(row.header)
            is PermissionRow.Entry -> (holder as EntryViewHolder).bind(row.entry, row.status)
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(header: PermissionGroupHeader) {
            itemView.findViewById<TextView>(R.id.tv_perm_group_title)?.text =
                if (header.titleRes != 0) itemView.context.getString(header.titleRes)
                else header.group.name
        }
    }

    inner class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(entry: PermissionEntry, status: PermissionStatus) {
            val ctx = itemView.context
            itemView.findViewById<TextView>(R.id.tv_perm_entry_title)?.text =
                if (entry.titleRes != 0) ctx.getString(entry.titleRes) else entry.id
            itemView.findViewById<TextView>(R.id.tv_perm_entry_desc)?.apply {
                if (entry.descriptionRes != 0) {
                    text = ctx.getString(entry.descriptionRes)
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
            val statusLabel = when (status) {
                PermissionStatus.GRANTED -> ctx.getString(R.string.perm_status_granted)
                PermissionStatus.DENIED -> ctx.getString(R.string.perm_status_denied)
                PermissionStatus.PERMANENTLY_DENIED -> ctx.getString(R.string.perm_status_permanently_denied)
                PermissionStatus.NOT_APPLICABLE -> ctx.getString(R.string.perm_status_not_applicable)
            }
            itemView.findViewById<TextView>(R.id.tv_perm_entry_status)?.text =
                ctx.getString(R.string.perm_current_status, statusLabel)
            itemView.findViewById<Button>(R.id.btn_perm_action)?.apply {
                text = when (status) {
                    PermissionStatus.GRANTED -> ctx.getString(R.string.perm_action_manage)
                    PermissionStatus.DENIED -> ctx.getString(R.string.perm_action_grant)
                    PermissionStatus.PERMANENTLY_DENIED -> ctx.getString(R.string.perm_action_settings)
                    PermissionStatus.NOT_APPLICABLE -> ""
                }
                // GRANT button is orange to signal an action is needed;
                // MANAGE / SETTINGS use the default primary color.
                backgroundTintList = ColorStateList.valueOf(
                    if (status == PermissionStatus.DENIED) {
                        ContextCompat.getColor(ctx, R.color.warning_color)
                    } else {
                        val tv = TypedValue()
                        ctx.theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
                        tv.data
                    }
                )
                visibility = if (status == PermissionStatus.NOT_APPLICABLE) View.GONE else View.VISIBLE
                setOnClickListener { onActionClick(entry, status) }
            }
        }
    }
}
