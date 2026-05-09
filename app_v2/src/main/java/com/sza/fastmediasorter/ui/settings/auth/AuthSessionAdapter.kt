package com.sza.fastmediasorter.ui.settings.auth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.AuthSessionDomain
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * S0116 §5.1 pillar K: row renderer for saved auth sessions.
 *
 * Single-tap delete (no confirmation) per the strategic spec — the user can
 * always re-add via the WebView auth dialog.
 */
class AuthSessionAdapter(
    private val onDelete: (host: String) -> Unit,
) : ListAdapter<AuthSessionDomain, AuthSessionAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_auth_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDelete)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val host: TextView = view.findViewById(R.id.tvAuthSessionHost)
        private val meta: TextView = view.findViewById(R.id.tvAuthSessionMeta)
        private val delete: ImageButton = view.findViewById(R.id.btnAuthSessionDelete)

        fun bind(item: AuthSessionDomain, onDelete: (String) -> Unit) {
            host.text = item.host
            val savedAtLocal = item.savedAt.atZone(ZoneId.systemDefault())
            val countText = host.context.resources.getQuantityString(
                R.plurals.auth_sessions_cookie_count,
                item.cookieCount,
                item.cookieCount,
            )
            meta.text = "$countText · ${DATE_FMT.format(savedAtLocal)}"
            delete.setOnClickListener { onDelete(item.host) }
            delete.contentDescription = host.context.getString(R.string.auth_sessions_delete_button_desc)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AuthSessionDomain>() {
            override fun areItemsTheSame(old: AuthSessionDomain, new: AuthSessionDomain): Boolean =
                old.host == new.host

            override fun areContentsTheSame(old: AuthSessionDomain, new: AuthSessionDomain): Boolean =
                old == new
        }
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
