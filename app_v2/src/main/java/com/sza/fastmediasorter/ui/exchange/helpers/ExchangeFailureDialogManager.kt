package com.sza.fastmediasorter.ui.exchange.helpers

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ViewExchangeFailurePanelBinding
import com.sza.fastmediasorter.domain.model.ExchangeDiagnosis
import com.sza.fastmediasorter.util.showBoundTo

/**
 * A failure of the My devices screen that has no dialog of its own (revoking a device, issuing a code): one
 * dialog holding the same panel, so a failure that was a toast reaches the person with its advice (strategic
 * ADR-2). A second failure replaces the one on screen.
 */
class ExchangeFailureDialogManager(private val activity: AppCompatActivity) {

    private var dialog: AlertDialog? = null

    fun show(diagnosis: ExchangeDiagnosis) {
        dialog?.dismiss()
        val binding = ViewExchangeFailurePanelBinding.inflate(LayoutInflater.from(activity))
        val padding = activity.resources.getDimensionPixelSize(R.dimen.dialog_padding_large)
        binding.root.setPadding(padding, 0, padding, 0)
        ExchangeFailurePanel(binding, activity).showFailure(diagnosis)
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.exchange_failure_dialog_title)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .setOnDismissListener { dialog = null }
            .showBoundTo(activity)
    }
}
