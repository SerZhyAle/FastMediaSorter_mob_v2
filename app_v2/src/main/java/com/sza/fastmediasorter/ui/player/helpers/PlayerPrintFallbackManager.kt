package com.sza.fastmediasorter.ui.player.helpers

import android.content.Intent
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.PlayerActivity
import timber.log.Timber
import java.io.File

/**
 * Fallback for [DocumentPrintManager]: when the system print dialog cannot be opened on a device
 * — Samsung One UI / Android 13+ reject [android.print.PrintManager.print] / [androidx.print.PrintHelper]
 * even with an Activity-backed context (S0145) — hand the already-prepared local file to the system
 * "Send to…" chooser so the user can still route it to a print target. The file is always local by
 * the time this is called (DocumentPrintManager materialises network/cloud sources first).
 */
class PlayerPrintFallbackManager(
    private val activity: PlayerActivity
) {

    /**
     * Launches a system share chooser for [file] (display [displayName], MIME [mimeType]).
     * Returns true if the chooser was started; false if even that failed (no handler,
     * FileProvider misconfiguration, security restriction) — caller then shows the plain
     * "print unavailable" notice.
     */
    fun shareForPrint(file: File, displayName: String, mimeType: String): Boolean {
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
            val chooser = Intent.createChooser(
                sendIntent,
                activity.getString(R.string.print_share_chooser_title)
            ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(chooser)
            true
        } catch (e: Exception) {
            Timber.e(e, "PlayerPrintFallbackManager: share chooser failed (${e.javaClass.simpleName}: ${e.message})")
            false
        }
    }
}
