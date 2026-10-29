package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.databinding.DialogBroadcastPrepareLinkBinding
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordRules
import com.sza.fastmediasorter.domain.model.PreparedLinkChoice
import com.sza.fastmediasorter.util.showBoundTo
import javax.inject.Inject

/**
 * S4247: the form that prepares a broadcast link before the start - the kind of broadcast for any source (В4), the
 * lifetime of 1 to 30 days (В5), the password choice of item R and the guests queued for the start (В7). A
 * password failing the rules stays in the field; the typed password never outlives the dialog.
 */
class BroadcastPrepareLinkDialogManager @Inject constructor() {

    fun interface OnPrepare {
        fun prepare(
            mode: BroadcastMode,
            days: Int,
            choice: PreparedLinkChoice,
            customPassword: String?,
            guests: List<String>,
        )
    }

    fun show(activity: AppCompatActivity, onPrepare: OnPrepare) {
        val view = DialogBroadcastPrepareLinkBinding.inflate(LayoutInflater.from(activity))
        val lifetimeListener = Slider.OnChangeListener { _, _, _ -> renderLifetime(activity, view) }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.broadcast_prepared_dialog_title)
            .setOnDismissListener {
                view.sliderPreparedLifetime.removeOnChangeListener(lifetimeListener)
                view.etPreparedPassword.text = null
            }
            .setView(view.root)
            .showBoundTo(activity) ?: return
        renderLifetime(activity, view)
        view.sliderPreparedLifetime.addOnChangeListener(lifetimeListener)
        view.groupPreparedPassword.setOnCheckedChangeListener { _, _ -> renderChoice(view) }
        renderChoice(view)
        view.etPreparedPassword.doAfterTextChanged { view.tilPreparedPassword.error = null }
        view.btnPreparedCancel.setOnClickListener { dialog.dismiss() }
        view.btnPreparedCreate.setOnClickListener {
            val choice = selectedChoice(view)
            val typed = view.etPreparedPassword.text?.toString()
            val verdict = typed?.let(BroadcastLinkPasswordRules::validate) ?: BroadcastLinkPasswordRules.Verdict.EMPTY
            if (choice == PreparedLinkChoice.CUSTOM && verdict != BroadcastLinkPasswordRules.Verdict.OK) {
                view.tilPreparedPassword.error = activity.getString(errorFor(verdict))
                view.etPreparedPassword.requestFocus()
                return@setOnClickListener
            }
            val guests = parseGuests(view.etPreparedGuests.text?.toString().orEmpty())
            val days = view.sliderPreparedLifetime.value.toInt()
            val password = typed.takeIf { choice == PreparedLinkChoice.CUSTOM }
            dialog.dismiss()
            onPrepare.prepare(selectedMode(view), days, choice, password, guests)
        }
    }

    private fun renderLifetime(activity: AppCompatActivity, view: DialogBroadcastPrepareLinkBinding) {
        val days = view.sliderPreparedLifetime.value.toInt()
        view.tvPreparedLifetime.text =
            activity.resources.getQuantityString(R.plurals.broadcast_prepared_days, days, days)
    }

    private fun renderChoice(view: DialogBroadcastPrepareLinkBinding) {
        val choice = selectedChoice(view)
        view.tilPreparedPassword.isVisible = choice == PreparedLinkChoice.CUSTOM
        view.tvPreparedPasswordNoneNote.isVisible = choice == PreparedLinkChoice.OPEN
        view.tilPreparedPassword.error = null
    }

    private fun selectedChoice(view: DialogBroadcastPrepareLinkBinding): PreparedLinkChoice =
        when (view.groupPreparedPassword.checkedRadioButtonId) {
            R.id.radioPreparedPasswordCustom -> PreparedLinkChoice.CUSTOM
            R.id.radioPreparedPasswordNone -> PreparedLinkChoice.OPEN
            else -> PreparedLinkChoice.GENERATED
        }

    private fun selectedMode(view: DialogBroadcastPrepareLinkBinding): BroadcastMode =
        when (view.groupPreparedMode.checkedChipId) {
            R.id.chipPreparedModeVideoAudio -> BroadcastMode.VIDEO_AUDIO
            R.id.chipPreparedModeVideo -> BroadcastMode.VIDEO_ONLY
            else -> BroadcastMode.AUDIO_ONLY
        }

    private fun errorFor(verdict: BroadcastLinkPasswordRules.Verdict): Int = when (verdict) {
        BroadcastLinkPasswordRules.Verdict.TOO_LONG -> R.string.broadcast_link_password_error_too_long
        BroadcastLinkPasswordRules.Verdict.CONTROL_CHARACTER -> R.string.broadcast_link_password_error_control
        else -> R.string.broadcast_link_password_error_empty
    }

    private fun parseGuests(text: String): List<String> =
        text.split(',', '\n').map(String::trim).filter(String::isNotEmpty).distinct()
}
