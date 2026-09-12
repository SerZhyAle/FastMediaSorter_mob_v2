package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import timber.log.Timber
import java.io.File

/**
 * Fallback for [DocumentPrintManager]: when the system print dialog cannot be opened on a device
 * - Samsung One UI / Android 13+ reject [android.print.PrintManager.print] / [androidx.print.PrintHelper]
 * even with an Activity-backed context (S0145) - hand the already-prepared local file to the system
 * "Send to…" chooser so the user can still route it to a print target. The file is always local by
 * the time this is called (DocumentPrintManager materialises network/cloud sources first).
 */
class PrintShareFallbackManager(
    private val activity: Activity
) {

    /**
     * Launches a system share chooser for [file] (display [displayName], MIME [mimeType]).
     * Returns true if the chooser was started; false if even that failed (no handler,
     * FileProvider misconfiguration, security restriction) - caller then shows the plain
     * "print unavailable" notice.
     */
    fun shareForPrint(
        file: File,
        displayName: String,
        mimeType: String,
        chooserTitle: String? = null
    ): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, displayName)
                putExtra(Intent.EXTRA_SUBJECT, displayName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // S2930: the caller is PrintDispatchActivity, which must NOT wrap its own context -
            // createConfigurationContext on it makes One UI reject PrintManager.print(). So the title
            // is read through a throwaway localized context instead of the activity's.
            val title = chooserTitle ?: LocaleHelper
                .localizedContext(activity, LocaleHelper.getLanguage(activity))
                .getString(R.string.print_share_chooser_title)
            val chooser = Intent.createChooser(sendIntent, title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(chooser)
            true
        } catch (e: Exception) {
            Timber.e(e, "PrintShareFallbackManager: share chooser failed (${e.javaClass.simpleName}: ${e.message})")
            false
        }
    }
}
