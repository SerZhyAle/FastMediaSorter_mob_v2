package com.sza.fastmediasorter.ui.player.helpers

import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.print.PrintDispatchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

private const val MAX_PRINT_FILE_SIZE_BYTES = 30L * 1024 * 1024 // 30 MB

/**
 * Manages document printing for a print host (the in-app player or a standalone document/text player,
 * see [DocumentPrintHost]). IMAGE/PDF/TEXT route through [PrintDispatchActivity] so Samsung/One UI
 * hosts wrapped by BaseActivity still reach the system print UI without tripping the "Can print only
 * from an activity" check. OFFICE prints through the host's internal viewer.
 *
 * Works for any resource type - local files, content:// URIs, SMB/SFTP/FTP, Google Drive,
 * Dropbox, OneDrive. All non-local paths are materialised to a local readable File via
 * [NetworkFileManager.prepareFileForRead]. That method returns either the original local
 * file, a copied content:// temp file (cacheDir), or a file from UnifiedFileCache - none of
 * which this class should delete: cacheDir is managed by the OS, UnifiedFileCache has its
 * own LRU eviction, and deleting a cached network file would invalidate the player's cache.
 */
class DocumentPrintManager(
    private val host: DocumentPrintHost,
    private val mediaCapabilities: MediaCapabilities
) {

    private val printFallbackManager = PrintShareFallbackManager(host.printHostActivity)

    /**
     * Entry point: print the currently open file.
     * Silently returns if the injected capability layer reports documents unsupported and the file is not an image.
     */
    fun printCurrentFile(mediaFile: MediaFile) {
        if (!mediaCapabilities.supportsDocuments && mediaFile.type != MediaType.IMAGE) {
            Timber.w("DocumentPrintManager: documents unsupported, skipping print for ${mediaFile.type}")
            return
        }

        Timber.i(
            "DocumentPrintManager: starting print, type=${mediaFile.type}, " +
                "file=${mediaFile.name}, path=${mediaFile.path}"
        )

        host.printHostActivity.lifecycleScope.launch {
            val file = prepareLocalFile(mediaFile) ?: return@launch
            if (host.printHostActivity.isFinishing || host.printHostActivity.isDestroyed) {
                Timber.w("DocumentPrintManager: activity finishing/destroyed after prepare, aborting")
                return@launch
            }
            val jobLabel = host.printHostActivity.getString(R.string.print_job_label, mediaFile.name)
            val sourceName = mediaFile.name
            when (mediaFile.type) {
                MediaType.PDF   -> printPdf(file, jobLabel, sourceName)
                MediaType.IMAGE -> printImage(file, jobLabel, sourceName)
                MediaType.TEXT  -> printText(file, jobLabel, sourceName)
                // S0301 Phase 05: Office documents print through the embedded viewer's own
                // WebView adapter (noLegal). The host returns false in market flavors / when
                // nothing is rendered, in which case we degrade to the standard fallback.
                MediaType.OFFICE_DOCUMENT -> {
                    if (!host.printOfficeDocument()) {
                        Timber.w("DocumentPrintManager: no internal Office print path, skipping ${mediaFile.name}")
                    }
                }
                else -> Timber.w("DocumentPrintManager: unsupported type for print: ${mediaFile.type}")
            }
        }
    }

    // ─── Resolution: any resource → local File ────────────────────────────────

    /**
     * Resolves [mediaFile] to a local readable [File]. For local paths returns the file as-is;
     * for content://, SMB/SFTP/FTP, and cloud resources downloads (or retrieves from cache)
     * via [NetworkFileManager]. Returns null and shows a message on failure or size overflow.
     */
    private suspend fun prepareLocalFile(mediaFile: MediaFile): File? {
        val file = try {
            withContext(Dispatchers.IO) {
                host.printNetworkFileManager.prepareFileForRead(mediaFile)
            }
        } catch (e: Exception) {
            e.errorUnlessCancellation("DocumentPrintManager: failed to prepare file for print: ${mediaFile.path}")
            showMessage(host.printHostActivity.getString(R.string.error_print_download_failed))
            return null
        }

        if (!file.exists() || file.length() == 0L) {
            Timber.e("DocumentPrintManager: prepared file is missing or empty: ${file.absolutePath}")
            showMessage(host.printHostActivity.getString(R.string.error_print_download_failed))
            return null
        }

        if (file.length() > MAX_PRINT_FILE_SIZE_BYTES) {
            Timber.w(
                "DocumentPrintManager: file too large for print " +
                    "(${file.length()} bytes > $MAX_PRINT_FILE_SIZE_BYTES)"
            )
            showMessage(host.printHostActivity.getString(R.string.error_print_file_too_large))
            return null
        }

        Timber.d(
            "DocumentPrintManager: prepared local file for print " +
                "(${file.length()} bytes): ${file.absolutePath}"
        )
        return file
    }

    // ─── PDF ──────────────────────────────────────────────────────────────────

    private fun printPdf(file: File, jobLabel: String, sourceName: String) {
        if (!PrintDispatchActivity.startPdf(
                context = host.printHostActivity,
                file = file,
                jobLabel = jobLabel,
                displayName = sourceName,
                chooserTitle = host.printHostActivity.getString(R.string.print_share_chooser_title),
                fallbackMessage = host.printHostActivity.getString(R.string.print_fallback_to_share),
                unavailableMessage = host.printHostActivity.getString(R.string.error_print_unavailable),
            )
        ) {
            showPrintFailedMessage(file, sourceName, "application/pdf")
        }
    }

    // ─── IMAGE ────────────────────────────────────────────────────────────────

    // S0919: route through PrintDispatchActivity (clean Activity context) so One UI reaches the system
    // print UI instead of "Can print only from an activity". The trampoline decodes [file] and owns the
    // share-for-print fallback; this file is cache/LRU-managed and must not be deleted here.
    private fun printImage(file: File, jobLabel: String, sourceName: String) {
        if (!PrintDispatchActivity.startImage(
                context = host.printHostActivity,
                file = file,
                jobLabel = jobLabel,
                displayName = sourceName,
                chooserTitle = host.printHostActivity.getString(R.string.print_share_chooser_title),
                fallbackMessage = host.printHostActivity.getString(R.string.print_fallback_to_share),
                unavailableMessage = host.printHostActivity.getString(R.string.error_print_unavailable),
            )
        ) {
            showPrintFailedMessage(file, sourceName, "image/*")
        }
    }

    // ─── TEXT ─────────────────────────────────────────────────────────────────

    private fun printText(file: File, jobLabel: String, sourceName: String) {
        if (!PrintDispatchActivity.startText(
                context = host.printHostActivity,
                file = file,
                jobLabel = jobLabel,
                displayName = sourceName,
                chooserTitle = host.printHostActivity.getString(R.string.print_share_chooser_title),
                fallbackMessage = host.printHostActivity.getString(R.string.print_fallback_to_share),
                unavailableMessage = host.printHostActivity.getString(R.string.error_print_unavailable),
            )
        ) {
            showPrintFailedMessage(file, sourceName, "text/plain")
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun showMessage(message: String) {
        host.showPrintMessage(message)
    }

    /**
     * Shown when the system print dialog could not be opened: offers the file to the system share
     * chooser as a fallback (S0145) and only falls back to the plain "print unavailable" notice if
     * even that fails.
     */
    private fun showPrintFailedMessage(sourceFile: File, sourceName: String, mimeType: String) {
        if (printFallbackManager.shareForPrint(sourceFile, sourceName, mimeType)) {
            showMessage(host.printHostActivity.getString(R.string.print_fallback_to_share))
        } else {
            showMessage(host.printHostActivity.getString(R.string.error_print_unavailable))
        }
    }

}
