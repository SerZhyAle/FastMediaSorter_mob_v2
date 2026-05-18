package com.sza.fastmediasorter.ui.share

import android.text.format.Formatter
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator

/**
 * S0003 §05.3: dedicated non-modal progress dialog for the link auto-download channel.
 *
 * Title text reflects the current ProgressState (Probing → starting; Downloading → downloading),
 * the LinearProgressIndicator switches between indeterminate (no total known) and determinate
 * (total > 0). Cancel fires `onCancel` exactly once.
 */
class LinkAutoDownloadProgressDialog(
    private val activity: AppCompatActivity,
    private val onCancel: () -> Unit,
) {

    private var dialog: AlertDialog? = null
    private var titleView: TextView? = null
    private var bytesView: TextView? = null
    private var progressBar: LinearProgressIndicator? = null
    private var cancelClicked = false

    fun show() {
        if (dialog != null) return
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_link_autodownload_progress, null)
        titleView = view.findViewById(R.id.titleText)
        bytesView = view.findViewById(R.id.bytesText)
        progressBar = view.findViewById(R.id.progressBar)
        view.findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener {
            if (cancelClicked) return@setOnClickListener
            cancelClicked = true
            onCancel()
            dismiss()
        }
        dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .setOnCancelListener {
                if (!cancelClicked) {
                    cancelClicked = true
                    onCancel()
                }
            }
            .show()
    }

    fun update(state: LinkAutoDownloadCoordinator.ProgressState) {
        if (dialog == null) return  // observer fired before show() - discard
        val bar = progressBar ?: return
        val bytes = bytesView ?: return
        val title = titleView ?: return
        when (state) {
            LinkAutoDownloadCoordinator.ProgressState.Probing -> {
                title.setText(R.string.link_autodownload_progress_starting)
                bar.isIndeterminate = true
                bytes.visibility = TextView.GONE
            }
            LinkAutoDownloadCoordinator.ProgressState.AnalyzingPage -> {
                title.setText(R.string.s0140_progress_analyzing_page)
                bar.isIndeterminate = true
                bytes.visibility = TextView.GONE
            }
            is LinkAutoDownloadCoordinator.ProgressState.Downloading -> {
                title.setText(R.string.link_autodownload_progress_downloading)
                val total = state.total
                if (total != null && total > 0) {
                    if (bar.isIndeterminate) bar.isIndeterminate = false
                    val pct = ((state.bytesRead * 100L) / total).toInt().coerceIn(0, 100)
                    bar.setProgressCompat(pct, true)
                    bytes.text = activity.getString(
                        R.string.link_autodownload_progress_bytes,
                        Formatter.formatShortFileSize(activity, state.bytesRead),
                        Formatter.formatShortFileSize(activity, total),
                    )
                } else {
                    bar.isIndeterminate = true
                    bytes.text = Formatter.formatShortFileSize(activity, state.bytesRead)
                }
                bytes.visibility = TextView.VISIBLE
            }
            is LinkAutoDownloadCoordinator.ProgressState.BatchDownloading -> {
                title.text = activity.getString(
                    R.string.s0117_progress_batch_item,
                    state.itemIndex,
                    state.itemCount,
                )
                val total = state.total
                if (total != null && total > 0) {
                    if (bar.isIndeterminate) bar.isIndeterminate = false
                    val pct = ((state.bytesRead * 100L) / total).toInt().coerceIn(0, 100)
                    bar.setProgressCompat(pct, true)
                    bytes.text = activity.getString(
                        R.string.link_autodownload_progress_bytes,
                        Formatter.formatShortFileSize(activity, state.bytesRead),
                        Formatter.formatShortFileSize(activity, total),
                    )
                } else {
                    bar.isIndeterminate = true
                    bytes.text = Formatter.formatShortFileSize(activity, state.bytesRead)
                }
                bytes.visibility = TextView.VISIBLE
            }
        }
    }

    fun dismiss() {
        dialog?.takeIf { it.isShowing }?.dismiss()
        dialog = null
        titleView = null
        bytesView = null
        progressBar = null
    }
}
