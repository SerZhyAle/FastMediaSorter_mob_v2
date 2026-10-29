package com.sza.fastmediasorter.ui.exchange.helpers

import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogExchangeAdminAccountBinding
import com.sza.fastmediasorter.databinding.DialogExchangeAdminLimitsBinding
import com.sza.fastmediasorter.databinding.DialogExchangeServerDevicesBinding
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccount
import com.sza.fastmediasorter.domain.model.ExchangeAdminKinds
import com.sza.fastmediasorter.domain.model.ExchangeAdminOwnLimits
import com.sza.fastmediasorter.domain.model.ExchangeAdminPolicy
import com.sza.fastmediasorter.domain.model.ExchangeAdminReplyCode
import com.sza.fastmediasorter.domain.model.ExchangeAdminSharedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringCodec
import com.sza.fastmediasorter.ui.exchange.ExchangeAdminAccountDevices
import com.sza.fastmediasorter.ui.exchange.ExchangeAdminPasswordChanged
import com.sza.fastmediasorter.ui.exchange.ExchangeServerAdminViewModel
import com.sza.fastmediasorter.util.showBoundTo
import timber.log.Timber

/**
 * Every dialog of the administration screen: the row overflow menus, the account forms (add, password, kind,
 * limits, delete), the devices of one account, the shared invitation and the policy. The view model owns every
 * decision; this class only asks for what a form needs and forwards it. A reply failure is worded by
 * [ExchangeMessageText.adminReply] and shown as a toast, never as a server text. The destructive confirms (delete
 * account, remove device, withdraw) open with ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive, whose
 * positive button carries the Widget.FastMediaSorter.Button.DialogDestructive role (S0538 builder seam).
 */
class ExchangeServerAdminDialogManager(
    private val activity: AppCompatActivity,
    private val viewModel: ExchangeServerAdminViewModel,
) {
    private var accountDialog: AlertDialog? = null
    private var limitsDialog: AlertDialog? = null
    private var devicesDialog: AlertDialog? = null
    private var devicesAdapter: ExchangeAdminRowAdapter? = null

    fun showAccountMenu(row: ExchangeAdminRow.Account, anchor: View) {
        val menu = PopupMenu(activity, anchor)
        menu.menu.add(Menu.NONE, MENU_PASSWORD, Menu.NONE, R.string.exchange_admin_change_password)
        menu.menu.add(Menu.NONE, MENU_KIND, Menu.NONE, R.string.exchange_admin_change_kind)
        menu.menu.add(Menu.NONE, MENU_LIMITS, Menu.NONE, R.string.exchange_admin_account_limits)
        menu.menu.add(Menu.NONE, MENU_DEVICES, Menu.NONE, R.string.exchange_admin_account_devices)
        menu.menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.exchange_admin_delete_account)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_PASSWORD -> showChangePassword(row.account.login)
                MENU_KIND -> showChangeKind(row.account)
                MENU_LIMITS -> showAccountLimits(row.account)
                MENU_DEVICES -> showDevices(row.account.login)
                MENU_DELETE -> confirmDeleteAccount(row.account.login)
            }
            true
        }
        menu.show()
    }

    fun showInvitationMenu(row: ExchangeAdminRow.Invitation, anchor: View) {
        val menu = PopupMenu(activity, anchor)
        menu.menu.add(Menu.NONE, MENU_WITHDRAW, Menu.NONE, R.string.exchange_admin_withdraw)
        menu.setOnMenuItemClickListener {
            confirmWithdraw(row.invitation.codeHash)
            true
        }
        menu.show()
    }

    fun showAddAccount() {
        val form = inflateAccountForm(
            R.string.exchange_admin_add_account,
            showLogin = true,
            showPassword = true,
            showKind = true,
        ) ?: return
        form.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val binding = form.binding
            val login = binding.etExchangeAdminLogin.text?.toString()?.trim().orEmpty()
            val password = binding.etExchangeAdminPassword.passwordArray()
            if (!ExchangeConnectionStringCodec.isLogin(login)) {
                binding.tilExchangeAdminLogin.error = activity.getString(R.string.exchange_admin_reply_bad_login)
                password.fill(CLEARED)
                return@setOnClickListener
            }
            binding.tilExchangeAdminLogin.error = null
            viewModel.addAccount(login, password, form.kind())
        }
    }

    fun showChangePassword(login: String) {
        val form = inflateAccountForm(
            R.string.exchange_admin_change_password,
            showLogin = false,
            showPassword = true,
            showKind = false,
        ) ?: return
        form.dialog.setMessage(activity.getString(R.string.exchange_admin_confirm_change_password, login))
        form.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            viewModel.setPassword(login, form.binding.etExchangeAdminPassword.passwordArray())
        }
    }

    fun showChangeKind(account: ExchangeAdminAccount) {
        val form = inflateAccountForm(
            R.string.exchange_admin_change_kind,
            showLogin = false,
            showPassword = false,
            showKind = true,
        ) ?: return
        form.selectKind(account.kind)
        form.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            viewModel.setKind(account.login, form.kind())
        }
    }

    fun showAccountLimits(account: ExchangeAdminAccount) {
        val holder = inflateLimitsForm(R.string.exchange_admin_account_limits, withCap = false) ?: return
        val binding = holder.binding
        binding.etExchangeAdminLimitDevices.setInt(account.ownLimits?.devicesPerAccount)
        binding.etExchangeAdminLimitResources.setInt(account.ownLimits?.resourcesPerAccount)
        binding.etExchangeAdminLimitBroadcasts.setInt(account.ownLimits?.broadcastsPerAccount)
        binding.etExchangeAdminLimitBroadcastsPerDevice.setInt(account.ownLimits?.broadcastsPerDevice)
        binding.etExchangeAdminLimitClipboard.setLong(account.ownLimits?.clipboardMaxBytes)
        holder.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val limits = ownLimits(binding) ?: return@setOnClickListener
            viewModel.setAccountLimits(account.login, limits)
        }
    }

    fun showDevices(login: String) {
        val binding = DialogExchangeServerDevicesBinding.inflate(LayoutInflater.from(activity))
        binding.textExchangeDevicesPairing.isVisible = false
        binding.btnExchangeDevicesPairing.isVisible = false
        val adapter = ExchangeAdminRowAdapter(
            onRowOpen = {},
            onRowOverflow = { row, _ -> (row as? ExchangeAdminRow.Device)?.let(::confirmRemoveDevice) },
        )
        binding.rvExchangeDevices.layoutManager = LinearLayoutManager(activity)
        binding.rvExchangeDevices.adapter = adapter
        devicesAdapter = adapter
        devicesDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.exchange_admin_account_devices) + ": " + login)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null)
            .setOnDismissListener {
                devicesDialog = null
                devicesAdapter = null
            }
            .showBoundTo(activity) ?: return
        viewModel.loadDevices(login)
    }

    fun showSharedInvitation(shared: ExchangeAdminSharedInvitation) {
        val form = inflateAccountForm(
            R.string.exchange_admin_section_shared,
            showLogin = false,
            showPassword = true,
            showKind = true,
        ) ?: return
        form.binding.tilExchangeAdminPassword.hint = activity.getString(R.string.exchange_admin_shared_password_label)
        form.selectKind(shared.kind ?: ExchangeAdminKinds.MEMBER)
        form.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            viewModel.setSharedInvitation(form.binding.etExchangeAdminPassword.passwordArray(), form.kind())
        }
    }

    fun showPolicy(policy: ExchangeAdminPolicy) {
        val holder = inflateLimitsForm(R.string.exchange_admin_section_limits, withCap = true) ?: return
        val binding = holder.binding
        binding.etExchangeAdminMaxAccounts.setInt(policy.maxAccounts)
        binding.etExchangeAdminLimitDevices.setInt(policy.defaults.devices)
        binding.etExchangeAdminLimitResources.setInt(policy.defaults.resources)
        binding.etExchangeAdminLimitBroadcasts.setInt(policy.defaults.broadcasts)
        binding.etExchangeAdminLimitBroadcastsPerDevice.setInt(policy.defaults.broadcastsPerDevice)
        binding.etExchangeAdminLimitClipboard.setLong(policy.defaults.clipboardBytes)
        holder.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val maxAccounts = binding.etExchangeAdminMaxAccounts.intField()
            if (maxAccounts == INVALID_INT) {
                binding.tilExchangeAdminMaxAccounts.error = activity.getString(R.string.exchange_admin_reply_bad_limit)
                return@setOnClickListener
            }
            binding.tilExchangeAdminMaxAccounts.error = null
            viewModel.setPolicy(maxAccounts, ownLimits(binding))
        }
    }

    fun showReplyFailure(code: ExchangeAdminReplyCode) {
        toast(activity.getString(ExchangeMessageText.adminReply(code)))
    }

    fun showPasswordSet(event: ExchangeAdminPasswordChanged) {
        toast(
            activity.resources.getQuantityString(
                R.plurals.exchange_admin_signed_out_devices,
                event.signedOutDevices,
                event.signedOutDevices,
            ),
        )
    }

    fun showDevicesList(event: ExchangeAdminAccountDevices) {
        devicesAdapter?.submitList(event.devices.map { device -> ExchangeAdminRow.Device(event.login, device) })
    }

    /** The session ended under the screen: every dialog of this manager loses its point and closes. */
    fun dismissTransient() {
        Timber.d("S4437: dismissTransient entered")
        accountDialog?.dismiss()
        limitsDialog?.dismiss()
        devicesDialog?.dismiss()
    }

    private fun confirmDeleteAccount(login: String) {
        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.exchange_admin_delete_account)
            .setMessage(activity.getString(R.string.exchange_admin_confirm_delete_account, login))
            .setPositiveButton(R.string.exchange_admin_delete_account) { _, _ -> viewModel.removeAccount(login) }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    private fun confirmRemoveDevice(row: ExchangeAdminRow.Device) {
        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.exchange_admin_remove_device)
            .setMessage(
                activity.getString(R.string.exchange_admin_confirm_remove_device, row.device.deviceName, row.login),
            )
            .setPositiveButton(R.string.exchange_admin_remove_device) { _, _ ->
                viewModel.revokeDevice(row.login, row.device.deviceId)
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    fun confirmWithdraw(codeHash: String) {
        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.exchange_admin_withdraw)
            .setMessage(R.string.exchange_admin_confirm_withdraw)
            .setPositiveButton(R.string.exchange_admin_withdraw) { _, _ -> viewModel.withdrawInvitation(codeHash) }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    private class AccountForm(val dialog: AlertDialog, val binding: DialogExchangeAdminAccountBinding) {
        fun kind(): String = when (binding.rgExchangeAdminKind.checkedRadioButtonId) {
            binding.rbExchangeAdminKindGuest.id -> ExchangeAdminKinds.GUEST
            else -> ExchangeAdminKinds.MEMBER
        }

        fun selectKind(kind: String) {
            val guest = kind == ExchangeAdminKinds.GUEST
            binding.rbExchangeAdminKindGuest.isChecked = guest
            binding.rbExchangeAdminKindMember.isChecked = !guest
        }
    }

    private fun inflateAccountForm(
        titleRes: Int,
        showLogin: Boolean,
        showPassword: Boolean,
        showKind: Boolean,
    ): AccountForm? {
        val binding = DialogExchangeAdminAccountBinding.inflate(LayoutInflater.from(activity))
        binding.tilExchangeAdminLogin.isVisible = showLogin
        binding.tilExchangeAdminPassword.isVisible = showPassword
        binding.rgExchangeAdminKind.isVisible = showKind
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.exchange_admin_save, null)
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { closed ->
                binding.etExchangeAdminPassword.setText(null)
                if (accountDialog === closed) accountDialog = null
            }
            .showBoundTo(activity) ?: return null
        accountDialog = dialog
        Timber.d("S4437: account form tracked for dismissTransient")
        return AccountForm(dialog, binding)
    }

    private class LimitsHolder(val dialog: AlertDialog, val binding: DialogExchangeAdminLimitsBinding)

    private fun inflateLimitsForm(titleRes: Int, withCap: Boolean): LimitsHolder? {
        val binding = DialogExchangeAdminLimitsBinding.inflate(LayoutInflater.from(activity))
        binding.tilExchangeAdminMaxAccounts.isVisible = withCap
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.exchange_admin_save, null)
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { closed -> if (limitsDialog === closed) limitsDialog = null }
            .showBoundTo(activity) ?: return null
        limitsDialog = dialog
        Timber.d("S4437: limits form tracked for dismissTransient")
        return LimitsHolder(dialog, binding)
    }

    // Invalid numbers are refused with the words of `bad-limit`; an empty field sends no member at all.
    private fun ownLimits(binding: DialogExchangeAdminLimitsBinding): ExchangeAdminOwnLimits? {
        val devices = binding.etExchangeAdminLimitDevices.intField()
        val resources = binding.etExchangeAdminLimitResources.intField()
        val broadcasts = binding.etExchangeAdminLimitBroadcasts.intField()
        val perDevice = binding.etExchangeAdminLimitBroadcastsPerDevice.intField()
        val clipboard = binding.etExchangeAdminLimitClipboard.longField()
        val badLimit = activity.getString(R.string.exchange_admin_reply_bad_limit)
        binding.tilExchangeAdminLimitDevices.error = if (devices == INVALID_INT) badLimit else null
        binding.tilExchangeAdminLimitResources.error = if (resources == INVALID_INT) badLimit else null
        binding.tilExchangeAdminLimitBroadcasts.error = if (broadcasts == INVALID_INT) badLimit else null
        binding.tilExchangeAdminLimitBroadcastsPerDevice.error = if (perDevice == INVALID_INT) badLimit else null
        binding.tilExchangeAdminLimitClipboard.error = if (clipboard == INVALID_LONG) badLimit else null
        val invalid = devices == INVALID_INT || resources == INVALID_INT || broadcasts == INVALID_INT ||
            perDevice == INVALID_INT || clipboard == INVALID_LONG
        return if (invalid) {
            null
        } else {
            ExchangeAdminOwnLimits(
                devicesPerAccount = devices.takeIf { it != INVALID_INT },
                resourcesPerAccount = resources.takeIf { it != INVALID_INT },
                broadcastsPerAccount = broadcasts.takeIf { it != INVALID_INT },
                broadcastsPerDevice = perDevice.takeIf { it != INVALID_INT },
                clipboardMaxBytes = clipboard.takeIf { it != INVALID_LONG },
            )
        }
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val MENU_PASSWORD = 1
        const val MENU_KIND = 2
        const val MENU_LIMITS = 3
        const val MENU_DEVICES = 4
        const val MENU_DELETE = 5
        const val MENU_WITHDRAW = 6
        const val CLEARED = '\u0000'
    }
}

// A number the wire does not accept is reported with the words of `bad-limit`; an empty field sends no member.
private const val INVALID_INT = Int.MIN_VALUE
private const val INVALID_LONG = Long.MIN_VALUE

private fun TextInputEditText.intField(): Int? {
    val text = text?.toString()?.trim().orEmpty()
    if (text.isEmpty()) return null
    return text.toIntOrNull() ?: INVALID_INT
}

private fun TextInputEditText.longField(): Long? {
    val text = text?.toString()?.trim().orEmpty()
    if (text.isEmpty()) return null
    return text.toLongOrNull() ?: INVALID_LONG
}

private fun TextInputEditText.setInt(value: Int?) {
    setText(value?.toString().orEmpty())
}

private fun TextInputEditText.setLong(value: Long?) {
    setText(value?.toString().orEmpty())
}

private fun TextInputEditText?.passwordArray(): CharArray {
    val chars = this?.text
    return CharArray(chars?.length ?: 0) { index -> chars?.get(index) ?: CLEARED }
}

private const val CLEARED = '\u0000'
