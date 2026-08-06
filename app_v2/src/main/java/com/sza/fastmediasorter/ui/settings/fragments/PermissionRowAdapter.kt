package com.sza.fastmediasorter.ui.settings.fragments

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
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
            bindStateIndicator(status)
            itemView.findViewById<Button>(R.id.btn_perm_action)?.apply {
                text = when (status) {
                    PermissionStatus.GRANTED -> ctx.getString(R.string.perm_action_manage)
                    PermissionStatus.NOT_YET_REQUESTED,
                    PermissionStatus.DENIED -> ctx.getString(R.string.perm_action_grant)
                    PermissionStatus.PERMANENTLY_DENIED -> ctx.getString(R.string.perm_action_settings)
                    PermissionStatus.NOT_APPLICABLE,
                    PermissionStatus.ASKED_EACH_TIME -> ""
                }
                // GRANT button is orange to signal an action is needed;
                // MANAGE / SETTINGS use the default primary color.
                backgroundTintList = ColorStateList.valueOf(
                    if (status == PermissionStatus.DENIED || status == PermissionStatus.NOT_YET_REQUESTED) {
                        ContextCompat.getColor(ctx, R.color.warning_color)
                    } else {
                        val tv = TypedValue()
                        ctx.theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
                        tv.data
                    }
                )
                // ASKED_EACH_TIME has no grant to offer (S1436): the row is informational, so the
                // button is hidden rather than shown disabled - a dead control reads as a bug.
                visibility = when (status) {
                    PermissionStatus.NOT_APPLICABLE,
                    PermissionStatus.ASKED_EACH_TIME -> View.GONE
                    else -> View.VISIBLE
                }
                setOnClickListener { onActionClick(entry, status) }
            }
        }

        /**
         * Shape and colour both encode the state and the content description carries it for TalkBack,
         * so the row stays readable for a colour-blind user and on a greyscale screenshot: a filled
         * check = granted, a hollow ring = not granted yet, a padlock = blocked in system settings.
         * NOT_APPLICABLE hides the indicator - such a row never reaches the list today, and an empty
         * slot is honest if one ever does. ASKED_EACH_TIME (S1436) reuses the not-granted shape and
         * colour but says so in words: the state is real and permanent, not a pending grant, and no
         * new icon is introduced for it because the row carries no action to distinguish.
         */
        private fun bindStateIndicator(status: PermissionStatus) {
            val indicator = itemView.findViewById<ImageView>(R.id.iv_perm_state) ?: return
            if (status == PermissionStatus.NOT_APPLICABLE) {
                indicator.visibility = View.INVISIBLE
                return
            }
            val ctx = indicator.context
            val (iconRes, colorRes, descRes) = when (status) {
                PermissionStatus.GRANTED -> Triple(
                    R.drawable.ic_perm_state_granted,
                    R.color.perm_state_granted,
                    R.string.perm_state_granted,
                )
                PermissionStatus.PERMANENTLY_DENIED -> Triple(
                    R.drawable.ic_perm_state_blocked,
                    R.color.perm_state_blocked,
                    R.string.perm_state_blocked,
                )
                PermissionStatus.ASKED_EACH_TIME -> Triple(
                    R.drawable.ic_perm_state_missing,
                    R.color.perm_state_missing,
                    R.string.perm_state_asked_each_time,
                )
                PermissionStatus.NOT_YET_REQUESTED,
                PermissionStatus.DENIED,
                PermissionStatus.NOT_APPLICABLE -> Triple(
                    R.drawable.ic_perm_state_missing,
                    R.color.perm_state_missing,
                    R.string.perm_state_missing,
                )
            }
            indicator.visibility = View.VISIBLE
            indicator.setImageResource(iconRes)
            ImageViewCompat.setImageTintList(
                indicator,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes)),
            )
            indicator.contentDescription = ctx.getString(descRes)
        }
    }
}
