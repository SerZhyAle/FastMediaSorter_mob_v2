package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogFdsecPasswordBinding
import com.sza.fastmediasorter.util.showBoundToHost
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import timber.log.Timber

/**
 * S3382: the one credential prompt for the FileDO container, serving both directions.
 *
 * Packing asks twice and compares, because a typo would lock the data behind a credential the owner
 * never meant and the format carries no recovery. Unpacking asks once. The credential leaves as a
 * `CharArray` and is wiped by the caller; it is never echoed and never logged.
 */
@ActivityScoped
class FdSecPasswordDialogManager @Inject constructor(
    @ActivityContext private val context: Context,
) {

    /** [OPEN] is viewing a container: the one direction that offers to remember the credential (S3397). */
    enum class Direction { ENCRYPT, DECRYPT, OPEN }

    /** The second callback argument is the remember box, always false outside [Direction.OPEN]. */
    fun ask(direction: Direction, onEntered: (CharArray, Boolean) -> Unit) {
        val binding = DialogFdsecPasswordBinding.inflate(LayoutInflater.from(context))
        val repeating = direction == Direction.ENCRYPT
        binding.tilFdSecPasswordRepeat.isVisible = repeating
        binding.cbFdSecRemember.isVisible = direction == Direction.OPEN
        Timber.d("S3481: fdsec remember option with leak line visible=%s", direction == Direction.OPEN)
        val titleRes = if (repeating) {
            R.string.filedo_password_title_encrypt
        } else {
            R.string.filedo_password_title_decrypt
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(binding.root)
            .create()
        bindEmptyWarning(binding, dialog)
        binding.fdSecActions.btnDialogCancel.setOnClickListener { dialog.dismiss() }
        binding.fdSecActions.btnDialogConfirm.setOnClickListener {
            confirm(binding, repeating, dialog::dismiss, onEntered)
        }
        dialog.showBoundToHost(context)
    }

    private fun confirm(
        binding: DialogFdsecPasswordBinding,
        repeating: Boolean,
        dismiss: () -> Unit,
        onEntered: (CharArray, Boolean) -> Unit,
    ) {
        val entered = binding.etFdSecPassword.text?.toString().orEmpty()
        val repeated = binding.etFdSecPasswordRepeat.text?.toString().orEmpty()
        if (repeating && entered != repeated) {
            binding.tilFdSecPasswordRepeat.error = context.getString(R.string.filedo_password_mismatch)
            return
        }
        val remember = binding.cbFdSecRemember.isVisible && binding.cbFdSecRemember.isChecked
        dismiss()
        onEntered(entered.toCharArray(), remember)
    }

    /**
     * The contract requires the no-secrecy statement wherever an empty credential is accepted, in
     * words that cannot be mistaken for reassurance - so it is shown while the field is empty
     * rather than once, after the fact.
     */
    private fun bindEmptyWarning(binding: DialogFdsecPasswordBinding, dialog: AlertDialog) {
        binding.tvFdSecEmptyWarning.isVisible = true
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.tvFdSecEmptyWarning.isVisible = s.isNullOrEmpty()
                binding.tilFdSecPasswordRepeat.error = null
            }
        }
        binding.etFdSecPassword.addTextChangedListener(watcher)
        dialog.setOnDismissListener { binding.etFdSecPassword.removeTextChangedListener(watcher) }
    }
}
