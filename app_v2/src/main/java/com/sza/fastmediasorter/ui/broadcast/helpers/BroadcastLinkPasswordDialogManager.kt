package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogBroadcastLinkPasswordBinding
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordMode
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordRules
import com.sza.fastmediasorter.domain.usecase.broadcast.RenewBroadcastLinkPasswordUseCase
import com.sza.fastmediasorter.domain.usecase.broadcast.SetBroadcastLinkPasswordUseCase
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S4405: the one dialog that chooses how the broadcast link is protected - automatic, none or the person's own -
 * opened from the share panel and from the broadcast settings row. Errors stay in the field and never close it;
 * "no password" is explained, never blocked. Holds no activity past the dialog it shows.
 */
class BroadcastLinkPasswordDialogManager @Inject constructor(
    private val setLinkPassword: SetBroadcastLinkPasswordUseCase,
    private val renewLinkPassword: RenewBroadcastLinkPasswordUseCase,
) {

    /**
     * [isLive] offers "Generate new" for the running broadcast; [onDone] receives the message the host shows once
     * the dialog has closed.
     */
    fun show(activity: AppCompatActivity, isLive: Boolean, onDone: (messageRes: Int) -> Unit) {
        activity.lifecycleScope.launch {
            val mode = setLinkPassword.currentMode()
            Timber.d("S4405: link password dialog opened mode=$mode live=$isLive")
            open(activity, isLive, mode, onDone)
        }
    }

    private fun open(
        activity: AppCompatActivity,
        isLive: Boolean,
        mode: BroadcastLinkPasswordMode,
        onDone: (Int) -> Unit,
    ) {
        val view = DialogBroadcastLinkPasswordBinding.inflate(LayoutInflater.from(activity))
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.broadcast_link_password_title)
            // The typed password must not outlive the dialog, not even in a detached view.
            .setOnDismissListener { view.etLinkPassword.text = null }
            .setView(view.root)
            .showBoundTo(activity) ?: return
        view.groupLinkPasswordMode.check(radioFor(mode))
        view.groupLinkPasswordMode.setOnCheckedChangeListener { _, _ -> renderMode(activity, view, isLive) }
        renderMode(activity, view, isLive)
        view.etLinkPassword.doAfterTextChanged { text ->
            view.tilLinkPassword.error = null
            renderWeakHint(activity, view, text?.toString().orEmpty())
        }
        view.btnLinkPasswordCancel.setOnClickListener { dialog.dismiss() }
        view.btnLinkPasswordGenerate.setOnClickListener {
            if (renewLinkPassword() != null) {
                dialog.dismiss()
                onDone(R.string.broadcast_link_password_applied)
            }
        }
        view.btnLinkPasswordApply.setOnClickListener {
            val chosen = selectedMode(view)
            val typed = view.etLinkPassword.text?.toString()
            activity.lifecycleScope.launch {
                when (val result = setLinkPassword(chosen, typed)) {
                    is SetBroadcastLinkPasswordUseCase.Result.Applied -> {
                        dialog.dismiss()
                        onDone(appliedMessage(isLive, chosen))
                    }
                    is SetBroadcastLinkPasswordUseCase.Result.Invalid ->
                        showFieldError(activity, view, errorFor(result.verdict))
                    SetBroadcastLinkPasswordUseCase.Result.StoreFailed ->
                        showFieldError(activity, view, R.string.broadcast_link_password_store_failed)
                }
            }
        }
    }

    private fun renderMode(activity: AppCompatActivity, view: DialogBroadcastLinkPasswordBinding, isLive: Boolean) {
        val mode = selectedMode(view)
        view.btnLinkPasswordGenerate.isVisible = isLive && mode == BroadcastLinkPasswordMode.AUTO
        view.textLinkPasswordNoneNote.isVisible = mode == BroadcastLinkPasswordMode.NONE
        view.tilLinkPassword.isVisible = mode == BroadcastLinkPasswordMode.CUSTOM
        view.tilLinkPassword.error = null
        renderWeakHint(activity, view, view.etLinkPassword.text?.toString().orEmpty())
    }

    private fun renderWeakHint(activity: AppCompatActivity, view: DialogBroadcastLinkPasswordBinding, text: String) {
        val weak = text.isNotEmpty() && BroadcastLinkPasswordRules.isWeak(text)
        view.tilLinkPassword.helperText =
            if (weak) activity.getString(R.string.broadcast_link_password_weak_hint) else null
    }

    private fun showFieldError(
        activity: AppCompatActivity,
        view: DialogBroadcastLinkPasswordBinding,
        @StringRes messageRes: Int,
    ) {
        view.tilLinkPassword.error = activity.getString(messageRes)
        view.etLinkPassword.requestFocus()
    }

    private fun selectedMode(view: DialogBroadcastLinkPasswordBinding): BroadcastLinkPasswordMode =
        when (view.groupLinkPasswordMode.checkedRadioButtonId) {
            R.id.radioLinkPasswordNone -> BroadcastLinkPasswordMode.NONE
            R.id.radioLinkPasswordCustom -> BroadcastLinkPasswordMode.CUSTOM
            else -> BroadcastLinkPasswordMode.AUTO
        }

    private fun radioFor(mode: BroadcastLinkPasswordMode): Int = when (mode) {
        BroadcastLinkPasswordMode.AUTO -> R.id.radioLinkPasswordAuto
        BroadcastLinkPasswordMode.NONE -> R.id.radioLinkPasswordNone
        BroadcastLinkPasswordMode.CUSTOM -> R.id.radioLinkPasswordCustom
    }

    private fun errorFor(verdict: BroadcastLinkPasswordRules.Verdict): Int = when (verdict) {
        BroadcastLinkPasswordRules.Verdict.TOO_LONG -> R.string.broadcast_link_password_error_too_long
        BroadcastLinkPasswordRules.Verdict.CONTROL_CHARACTER -> R.string.broadcast_link_password_error_control
        else -> R.string.broadcast_link_password_error_empty
    }

    private fun appliedMessage(isLive: Boolean, mode: BroadcastLinkPasswordMode): Int = when {
        !isLive -> R.string.broadcast_link_password_saved
        mode == BroadcastLinkPasswordMode.NONE -> R.string.broadcast_link_password_applied_open
        else -> R.string.broadcast_link_password_applied
    }

    companion object {
        /** The settings row's value text for [mode]. */
        @StringRes
        fun labelFor(mode: BroadcastLinkPasswordMode): Int = when (mode) {
            BroadcastLinkPasswordMode.AUTO -> R.string.broadcast_link_password_mode_auto
            BroadcastLinkPasswordMode.NONE -> R.string.broadcast_link_password_mode_none
            BroadcastLinkPasswordMode.CUSTOM -> R.string.broadcast_link_password_mode_custom
        }
    }
}
