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
import com.sza.fastmediasorter.domain.repository.AuthAccountDomain
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * S0155: flat list adapter for the "Saved authorizations" settings screen.
 * Renders per-host group headers ([Item.HostHeader]) and per-account rows
 * ([Item.AccountRow]) from a list built by [buildItems].
 */
class AuthAccountGroupAdapter(
    private val onAddAccount: (host: String, loginUrl: String) -> Unit,
    private val onDelete: (host: String, account: AuthAccountDomain) -> Unit,
    private val onRename: (host: String, accountId: String, currentName: String) -> Unit,
    private val onRelogin: (host: String, accountId: String, loginUrl: String) -> Unit,
) : ListAdapter<AuthAccountGroupAdapter.Item, RecyclerView.ViewHolder>(DIFF) {

    sealed interface Item {
        data class HostHeader(val group: AuthHostGroup) : Item
        data class AccountRow(val host: String, val account: AuthAccountDomain) : Item
    }

    /** Flatten [groups] into a mixed list: one [Item.HostHeader] + N [Item.AccountRow] per group. */
    fun buildItems(groups: List<AuthHostGroup>): List<Item> = buildList {
        groups.forEach { group ->
            add(Item.HostHeader(group))
            group.accounts.forEach { account -> add(Item.AccountRow(group.host, account)) }
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is Item.HostHeader -> TYPE_HEADER
        is Item.AccountRow -> TYPE_ACCOUNT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HostHeaderViewHolder(
                inflater.inflate(R.layout.item_auth_host_group, parent, false),
            )
            else -> AccountRowViewHolder(
                inflater.inflate(R.layout.item_auth_account, parent, false),
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is Item.HostHeader -> (holder as HostHeaderViewHolder).bind(item.group)
            is Item.AccountRow -> (holder as AccountRowViewHolder).bind(item.host, item.account)
        }
    }

    // ── Host header ──────────────────────────────────────────────────────────

    inner class HostHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val hostName: TextView = view.findViewById(R.id.tvAuthHostName)
        private val addBtn: ImageButton = view.findViewById(R.id.btnAddAccount)

        fun bind(group: AuthHostGroup) {
            // Prefer brand display name (e.g. "Instagram") over bare hostname.
            hostName.text = group.resource?.displayName ?: group.host
            addBtn.setOnClickListener {
                onAddAccount(group.host, group.resource?.loginUrl.orEmpty())
            }
        }
    }

    // ── Account row ──────────────────────────────────────────────────────────

    inner class AccountRowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val displayName: TextView = view.findViewById(R.id.tvAccountDisplayName)
        private val meta: TextView = view.findViewById(R.id.tvAccountMeta)
        private val reloginBtn: ImageButton = view.findViewById(R.id.btnAccountRelogin)
        private val renameBtn: ImageButton = view.findViewById(R.id.btnAccountRename)
        private val deleteBtn: ImageButton = view.findViewById(R.id.btnAccountDelete)

        fun bind(host: String, account: AuthAccountDomain) {
            val ctx = itemView.context
            if (account.isDismissed) {
                val dismissedLabel = ctx.getString(R.string.s0157_dismissed_label)
                displayName.text = account.visibleLabel(dismissedLabel)
                val metaText = if (account.displayName.isBlank()) "" else dismissedLabel
                meta.text = metaText
                meta.visibility = if (metaText.isBlank()) View.GONE else View.VISIBLE
                reloginBtn.visibility = View.GONE
                renameBtn.visibility = View.GONE
            } else {
                displayName.text = account.displayName
                val cookieText = ctx.resources.getQuantityString(
                    R.plurals.auth_sessions_cookie_count,
                    account.cookieCount,
                    account.cookieCount,
                )
                val savedAtLocal = account.savedAt.atZone(ZoneId.systemDefault())
                val metaText = StringBuilder("$cookieText · ${DATE_FMT.format(savedAtLocal)}")
                val lastUsedText = account.lastUsedAt
                    ?.let { ctx.getString(R.string.s0157_last_used_fmt, DATE_FMT.format(it.atZone(ZoneId.systemDefault()))) }
                    ?: ctx.getString(R.string.s0157_last_used_never)
                metaText.append(" · $lastUsedText")
                meta.text = metaText
                meta.visibility = View.VISIBLE
                reloginBtn.visibility = View.VISIBLE
                renameBtn.visibility = View.VISIBLE
            }

            val loginUrl = itemView.context.let {
                // Host group is not available here; loginUrl resolved via KnownAuthResources.
                com.sza.fastmediasorter.data.link.auth.KnownAuthResources.matchHost(host)
                    ?.loginUrl.orEmpty()
            }
            reloginBtn.setOnClickListener { onRelogin(host, account.accountId, loginUrl) }
            renameBtn.setOnClickListener { onRename(host, account.accountId, account.displayName) }
            deleteBtn.setOnClickListener { onDelete(host, account) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ACCOUNT = 1

        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean = when {
                oldItem is Item.HostHeader && newItem is Item.HostHeader ->
                    oldItem.group.host == newItem.group.host
                oldItem is Item.AccountRow && newItem is Item.AccountRow ->
                    oldItem.host == newItem.host && oldItem.account.accountId == newItem.account.accountId
                else -> false
            }

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem == newItem
        }
    }
}
