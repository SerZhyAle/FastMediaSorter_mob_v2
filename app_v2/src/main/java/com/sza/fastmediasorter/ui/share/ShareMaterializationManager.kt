package com.sza.fastmediasorter.ui.share

import android.app.Activity
import android.app.Dialog
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.domain.usecase.MaterializeShareContentUseCase
import com.sza.fastmediasorter.ui.dialog.MaterialProgressDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges «Send to..» dispatch to an on-demand remote-file download (S0493). For a remote
 * [ShareableContent] it shows a determinate progress dialog, downloads a local cache copy, then
 * invokes [onReady] with the localized content. Local content passes through untouched. Back-press
 * cancels the download; a failure surfaces a "could not prepare" toast and does not call [onReady].
 *
 * Singleton with no retained state - the host [Activity] is supplied per call and provides both the
 * dialog context and the lifecycle scope the download runs on.
 */
@Singleton
class ShareMaterializationManager @Inject constructor(
    private val materialize: MaterializeShareContentUseCase,
) {
    fun materializeThenRun(
        activity: Activity,
        content: ShareableContent,
        onReady: (ShareableContent) -> Unit,
    ) {
        if (!content.requiresMaterialization) {
            onReady(content)
            return
        }
        val lifecycleOwner = activity as? LifecycleOwner
        if (lifecycleOwner == null) {
            Timber.w("Send-to: host is not a LifecycleOwner, cannot prepare remote file")
            Toast.makeText(activity, R.string.error_share_download_failed, Toast.LENGTH_SHORT).show()
            return
        }

        if (activity.isFinishing || activity.isDestroyed) return

        // SMB and FTP report byte-level progress through the download stack; SFTP does not, so show an
        // honest indeterminate spinner for SFTP instead of a determinate bar frozen at 0% (S0493, S0496).
        val determinate = content.sourcePath?.let { it.startsWith("smb:") || it.startsWith("ftp:") } == true
        Timber.d("S0496: Send-to progress determinate=$determinate src=${content.sourcePath}")
        val dialog = MaterialProgressDialog(activity).apply {
            setTitle(activity.getString(R.string.downloading_file))
            setMessage(content.displayName ?: "")
            setProgressStyle(
                if (determinate) MaterialProgressDialog.STYLE_HORIZONTAL else MaterialProgressDialog.STYLE_SPINNER
            )
            max = 100
        }
        try {
            dialog.show()
        } catch (e: WindowManager.BadTokenException) {
            Timber.w(e, "Send-to: could not show preparation dialog - host window gone")
            return
        }

        val onProgress: ((Int) -> Unit)? = if (determinate) {
            { percent ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.runOnUiThread { dialog.progress = percent }
                }
            }
        } else {
            null
        }

        val job = lifecycleOwner.lifecycleScope.launch {
            try {
                val result = materialize.execute(content, onProgress)
                dialog.dismissSafely()
                result
                    .onSuccess { onReady(it) }
                    .onFailure {
                        Timber.w(it, "Send-to: could not prepare remote file")
                        Toast.makeText(activity, R.string.error_share_download_failed, Toast.LENGTH_SHORT).show()
                    }
            } catch (e: CancellationException) {
                dialog.dismissSafely()
                throw e
            } catch (e: Exception) {
                dialog.dismissSafely()
                Timber.e(e, "Send-to: remote file preparation crashed")
                Toast.makeText(activity, R.string.error_share_download_failed, Toast.LENGTH_SHORT).show()
            }
        }

        // Make the preparation cancelable via back press (the dialog forces non-cancelable in onCreate).
        dialog.setCancelable(true)
        dialog.setOnCancelListener { job.cancel() }
    }

    // The host window may already be detached when the coroutine resumes (rotation / finish); a bare
    // dismiss() then throws. Guard on isShowing and swallow the detached-window race.
    private fun Dialog.dismissSafely() {
        if (!isShowing) return
        try {
            dismiss()
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Send-to: dialog dismiss skipped - window already detached")
        }
    }
}
