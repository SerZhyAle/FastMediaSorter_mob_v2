package com.sza.fastmediasorter.ui.systeminfo.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.core.systeminfo.SystemInfoReport
import com.sza.fastmediasorter.domain.usecase.GatherSystemInfoUseCase
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensSelectionReporter
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** S1261: bounded wait for CameraX init on the report path - degrade, never hang the dialog. */
private const val CAMERA_APP_VIEW_TIMEOUT_SECONDS = 3L

/**
 * The one place that turns a [Context] into a shown system-information report.
 *
 * S1733 moved this out of the settings fragment's log helper, where it was a private method reachable
 * only from that one screen. System information is now a program with several entrances - the programs
 * menu, the programs panel, a launcher or quick-access tile - and each of them has to show the same
 * report. Two copies of the gather-and-show sequence would be two places deciding what the report
 * contains, and they would drift.
 */
class SystemInfoDialogManager @Inject constructor(
    private val gatherSystemInfoUseCase: GatherSystemInfoUseCase
) {

    /** Collects the report off the main thread. The use case reads disk and binders and never throws. */
    suspend fun gather(context: Context): SystemInfoReport {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            gatherSystemInfoUseCase(buildCameraAppView(appContext))
        }
    }

    /**
     * Shows [report] and returns the dialog, so a caller whose whole reason for existing is this dialog
     * can finish itself when the user closes it.
     *
     * The copy, share and save actions stay on the masked text; the full report keeps its own confirmed
     * action, and only when there is anything sensitive to reveal.
     */
    fun show(context: Context, report: SystemInfoReport): AlertDialog? = ScrollableTextDialog.show(
        context = context,
        title = context.getString(R.string.settings_system_info_title),
        message = report.maskedText,
        inlineActionButtonText = if (report.hasSensitive) {
            context.getString(R.string.system_info_copy_full_report)
        } else {
            null
        },
        onInlineActionClick = if (report.hasSensitive) {
            { confirmAndCopyFullReport(context, report.fullText) }
        } else {
            null
        }
    )

    private fun confirmAndCopyFullReport(context: Context, fullText: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.system_info_reveal_confirm_title)
            .setMessage(R.string.system_info_reveal_confirm_message)
            .setPositiveButton(R.string.system_info_copy_full_report) { _, _ ->
                context.copyTextToClipboard("System info", fullText)
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(context)
    }

    /**
     * S1261: what the capture screen derived from the platform camera tree, or why it could not.
     * Values stay technical-English on purpose - the section is pasted back verbatim in reports.
     * Never throws and never hangs: CameraX init gets a bounded wait and degrades to an error line.
     */
    private fun buildCameraAppView(context: Context): List<String> {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return listOf("camera permission not granted")
        return runCatching {
            val provider = ProcessCameraProvider.getInstance(context)
                .get(CAMERA_APP_VIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            CameraLensSelectionReporter().report(provider)
        }.getOrElse { error ->
            Timber.w(error, "System info: camera app view unavailable")
            listOf("unavailable: ${error.javaClass.simpleName}")
        }
    }
}
