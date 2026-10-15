package com.sza.fastmediasorter.ui.broadcast.helpers

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogBroadcastGuestsBinding
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastGrant
import com.sza.fastmediasorter.domain.usecase.broadcast.ManageBroadcastGuestsUseCase
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The live broadcast's guests (contract DEVICE-EXCHANGE item BD): gives the broadcast to a guest login, lists every
 * grant with the server's answer and withdraws one alone. Holds no activity past the dialog it shows.
 */
class BroadcastGuestsDialogManager(private val guests: ManageBroadcastGuestsUseCase) {

    fun show(activity: AppCompatActivity) {
        val view = DialogBroadcastGuestsBinding.inflate(LayoutInflater.from(activity))
        var watch: Job? = null
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.broadcast_guests_title)
            .setView(view.root)
            .setOnDismissListener { watch?.cancel() }
            .showBoundTo(activity) ?: return
        watch = activity.collectOnLifecycle(guests.grants) { list -> renderGrants(activity, view, list) }
        view.btnBroadcastGuestCancel.setOnClickListener { dialog.dismiss() }
        view.btnBroadcastGuestGive.setOnClickListener { give(activity, view) }
    }

    private fun give(activity: AppCompatActivity, view: DialogBroadcastGuestsBinding) {
        val login = view.etBroadcastGuestLogin.text?.toString()?.trim().orEmpty()
        if (!DeviceExchangeAccountEntry.isValidLogin(login)) {
            view.tilBroadcastGuestLogin.error = activity.getString(R.string.broadcast_guests_login_invalid)
            view.etBroadcastGuestLogin.requestFocus()
            return
        }
        activity.lifecycleScope.launch {
            if (guests.give(login)) {
                view.tilBroadcastGuestLogin.error = null
                view.etBroadcastGuestLogin.text = null
            } else {
                view.tilBroadcastGuestLogin.error = activity.getString(R.string.broadcast_guests_give_failed)
            }
        }
    }

    private fun renderGrants(
        activity: AppCompatActivity,
        view: DialogBroadcastGuestsBinding,
        list: List<ExchangeBroadcastGrant>,
    ) {
        val container = view.containerBroadcastGuests
        container.removeAllViews()
        view.textBroadcastGuestsEmpty.isVisible = list.isEmpty()
        list.forEach { grant -> container.addView(grantRow(activity, grant)) }
    }

    private fun grantRow(activity: AppCompatActivity, grant: ExchangeBroadcastGrant): LinearLayout {
        val context: Context = activity
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = grant.guest })
            addView(
                TextView(context).apply { setText(stateText(grant.state)) }
            )
        }
        val withdraw = MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            setText(R.string.broadcast_guests_withdraw)
            contentDescription = context.getString(R.string.broadcast_guests_withdraw_cd, grant.guest)
            minHeight = context.resources.getDimensionPixelSize(R.dimen.touch_target_min_height)
            setOnClickListener { activity.lifecycleScope.launch { guests.withdraw(grant.grantId) } }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(withdraw)
        }
    }

    private fun stateText(state: ExchangeBroadcastGrant.State): Int = when (state) {
        ExchangeBroadcastGrant.State.PENDING -> R.string.broadcast_guests_state_pending
        ExchangeBroadcastGrant.State.GRANTED -> R.string.broadcast_guests_state_granted
        ExchangeBroadcastGrant.State.REFUSED -> R.string.broadcast_guests_state_refused
    }
}
