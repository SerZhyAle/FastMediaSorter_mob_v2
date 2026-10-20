package com.sza.fastmediasorter.ui.exchange.helpers

import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogExchangeChangePasswordBinding
import com.sza.fastmediasorter.databinding.DialogExchangeDeleteAccountBinding
import com.sza.fastmediasorter.databinding.ViewExchangeFailurePanelBinding
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.ui.exchange.ExchangeAccountActionState
import com.sza.fastmediasorter.ui.exchange.MyDevicesViewModel
import com.sza.fastmediasorter.util.showBoundTo

/**
 * S4223: the account dialogs of a server card - changing the password with the old one (19 item AT) and deleting
 * the account (19 item AU). Each stays open while the server answers and shows a refusal in place, so a wrong
 * password is typed again without opening the dialog anew. The view model decides; this class only shows.
 */
class ExchangeAccountDialogManager(
    private val activity: AppCompatActivity,
    private val viewModel: MyDevicesViewModel,
) {

    private var dialog: AlertDialog? = null
    private var panel: ExchangeFailurePanel? = null
    private var lastState: ExchangeAccountActionState? = null

    fun showChangePassword(entry: DeviceExchangeAccountEntry) {
        viewModel.resetAccountAction()
        val binding = DialogExchangeChangePasswordBinding.inflate(LayoutInflater.from(activity))
        val shown = open(
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.exchange_password_title)
                .setView(binding.root)
                .setPositiveButton(R.string.exchange_password_submit, null),
            binding.exchangeFailurePanel,
        ) ?: return
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val old = binding.etExchangePasswordOld.text?.toString().orEmpty()
            val new = binding.etExchangePasswordNew.text?.toString().orEmpty()
            val repeat = binding.etExchangePasswordRepeat.text?.toString().orEmpty()
            binding.tilExchangePasswordOld.error = emptyError(old)
            binding.tilExchangePasswordNew.error = emptyError(new)
            binding.tilExchangePasswordRepeat.error =
                if (repeat != new) activity.getString(R.string.exchange_signin_password_mismatch) else null
            if (old.isNotEmpty() && new.isNotEmpty() && repeat == new) viewModel.changePassword(entry, old, new)
        }
    }

    fun showDeleteAccount(entry: DeviceExchangeAccountEntry) {
        viewModel.resetAccountAction()
        val binding = DialogExchangeDeleteAccountBinding.inflate(LayoutInflater.from(activity))
        binding.btnExchangeDeleteHelp.setOnClickListener {
            MediaExchangePortalLinks.open(activity, MediaExchangePortalLinks.Page.ACCOUNT_DELETION)
        }
        // Deleting is the dangerous choice: the dialog opens on the safe button (S4051 owner rule).
        val shown = open(
            MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
                .setTitle(activity.getString(R.string.exchange_delete_title, entry.login))
                .setView(binding.root)
                .setPositiveButton(R.string.exchange_delete_submit, null),
            binding.exchangeFailurePanel,
        ) ?: return
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = binding.etExchangeDeletePassword.text?.toString().orEmpty()
            binding.tilExchangeDeletePassword.error = emptyError(password)
            if (password.isNotEmpty()) viewModel.deleteAccount(entry, password)
        }
    }

    fun render(state: ExchangeAccountActionState) {
        val shown = dialog ?: return
        // A collector restarted by the lifecycle replays the same state; it was already shown.
        if (state === lastState) return
        lastState = state
        shown.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = state !is ExchangeAccountActionState.InProgress
        when (state) {
            ExchangeAccountActionState.InProgress ->
                panel?.showProgress(activity.getString(R.string.exchange_account_action_progress))
            is ExchangeAccountActionState.Failed -> panel?.showFailure(state.diagnosis)
            else -> panel?.hide()
        }
        if (state is ExchangeAccountActionState.Done) {
            shown.dismiss()
            Toast.makeText(activity, MyDevicesDialogManager.resolve(activity, state.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun open(
        builder: MaterialAlertDialogBuilder,
        panelBinding: ViewExchangeFailurePanelBinding,
    ): AlertDialog? {
        val shown = builder
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                dialog = null
                panel = null
                lastState = null
                viewModel.resetAccountAction()
            }
            .showBoundTo(activity) ?: return null
        dialog = shown
        panel = ExchangeFailurePanel(panelBinding, activity)
        return shown
    }

    private fun emptyError(value: String): String? =
        if (value.isEmpty()) activity.getString(R.string.exchange_password_empty) else null
}
