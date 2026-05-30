package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import timber.log.Timber

/**
 * noLegal Office print adapter (S0301 Phase 05).
 *
 * The embedded Office viewer renders documents into a read-only [WebView]; this adapter prints
 * exactly that rendered surface through Android's framework [PrintManager] +
 * [WebView.createPrintDocumentAdapter]. This keeps Office printing on a real document path
 * instead of pretending every Office file is a PDF, and it never mutates the source file.
 *
 * Kept in `src/noLegal` so no Office-specific print code is linked into market builds; the
 * shared [OfficeDocumentViewerHost] exposes only a flavor-safe `print()` seam.
 */
internal class OfficeDocumentPrintAdapter {

    /**
     * Dispatch a print job for [webView]'s current content.
     *
     * @return true when a job was handed to the framework PrintManager; false when printing is
     * not possible (no PrintManager available). Any failure degrades gracefully without crashing.
     */
    fun print(webView: WebView, jobName: String): Boolean {
        val context: Context = webView.context
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Timber.w("OfficeDocumentPrintAdapter: PRINT_SERVICE unavailable")
            return false
        }
        return runCatching {
            val adapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(
                jobName,
                adapter,
                PrintAttributes.Builder().build(),
            )
            true
        }.getOrElse { error ->
            Timber.w(error, "OfficeDocumentPrintAdapter: failed to dispatch print job")
            false
        }
    }
}
