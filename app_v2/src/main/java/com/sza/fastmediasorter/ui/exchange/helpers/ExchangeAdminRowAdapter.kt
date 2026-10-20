package com.sza.fastmediasorter.ui.exchange.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemExchangeAdminRowBinding
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccount
import com.sza.fastmediasorter.domain.model.ExchangeAdminDevice
import com.sza.fastmediasorter.domain.model.ExchangeAdminInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminKinds

/**
 * One row of the administration screen: an account, an invitation shown without its hash, or a device of one
 * account. The words come from the S4356 phase 03 strings. The summary of an account reads the effective limits
 * object only - the account's own limits belong to the limits dialog, never to this row.
 */
sealed interface ExchangeAdminRow {
    data class Account(val account: ExchangeAdminAccount) : ExchangeAdminRow
    data class Invitation(val invitation: ExchangeAdminInvitation) : ExchangeAdminRow
    data class Device(val login: String, val device: ExchangeAdminDevice) : ExchangeAdminRow
}

/** The one row shape of the accounts, invitations and devices lists: title, subtitle, overflow. */
class ExchangeAdminRowAdapter(
    private val onRowOpen: (ExchangeAdminRow) -> Unit,
    private val onRowOverflow: (ExchangeAdminRow, View) -> Unit,
) : ListAdapter<ExchangeAdminRow, ExchangeAdminRowAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemExchangeAdminRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int): Unit = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemExchangeAdminRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ExchangeAdminRow) {
            val context = binding.root.context
            binding.textExchangeAdminRowTitle.text = title(context, row)
            binding.textExchangeAdminRowSubtitle.text = subtitle(context, row)
            binding.root.setOnClickListener { onRowOpen(row) }
            binding.btnExchangeAdminRowOverflow.setOnClickListener { anchor -> onRowOverflow(row, anchor) }
        }
    }

    private fun title(context: Context, row: ExchangeAdminRow): String = when (row) {
        is ExchangeAdminRow.Account -> row.account.login
        is ExchangeAdminRow.Invitation -> kindText(context, row.invitation.kind)
        is ExchangeAdminRow.Device -> row.device.deviceName
    }

    private fun subtitle(context: Context, row: ExchangeAdminRow): String = when (row) {
        is ExchangeAdminRow.Account -> context.getString(
            R.string.exchange_admin_account_summary,
            kindText(context, row.account.kind),
            row.account.devices,
            row.account.effective.devices,
        )
        is ExchangeAdminRow.Invitation -> listOfNotNull(
            row.invitation.createdAt,
            context.getString(
                if (row.invitation.used) {
                    R.string.exchange_admin_invitation_used
                } else {
                    R.string.exchange_admin_invitation_unused
                },
            ),
            row.invitation.expectedLogin?.let { expected ->
                context.getString(R.string.exchange_admin_expected_login_label) + " " + expected
            },
        ).joinToString("\n")
        is ExchangeAdminRow.Device -> listOfNotNull(
            context.getString(
                if (row.device.online) {
                    R.string.exchange_admin_device_online
                } else {
                    R.string.exchange_admin_device_offline
                },
            ),
            if (row.device.signedOut) context.getString(R.string.exchange_admin_device_signed_out) else null,
            row.device.lastSeen?.let { seen -> context.getString(R.string.exchange_admin_device_last_seen, seen) },
        ).joinToString("\n")
    }

    private fun kindText(context: Context, kind: String): String = when (kind) {
        ExchangeAdminKinds.MEMBER -> context.getString(R.string.exchange_admin_kind_member)
        ExchangeAdminKinds.GUEST -> context.getString(R.string.exchange_admin_kind_guest)
        else -> kind
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ExchangeAdminRow>() {
            override fun areItemsTheSame(oldItem: ExchangeAdminRow, newItem: ExchangeAdminRow): Boolean = when {
                oldItem is ExchangeAdminRow.Account && newItem is ExchangeAdminRow.Account ->
                    oldItem.account.login == newItem.account.login
                oldItem is ExchangeAdminRow.Invitation && newItem is ExchangeAdminRow.Invitation ->
                    oldItem.invitation.codeHash == newItem.invitation.codeHash
                oldItem is ExchangeAdminRow.Device && newItem is ExchangeAdminRow.Device ->
                    oldItem.device.deviceId == newItem.device.deviceId
                else -> false
            }

            override fun areContentsTheSame(oldItem: ExchangeAdminRow, newItem: ExchangeAdminRow): Boolean =
                oldItem == newItem
        }
    }
}
