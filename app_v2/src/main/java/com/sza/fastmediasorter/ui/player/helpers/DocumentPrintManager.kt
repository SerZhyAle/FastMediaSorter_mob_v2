package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import androidx.print.PrintHelper
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val MAX_PRINT_FILE_SIZE_BYTES = 30L * 1024 * 1024 // 30 MB

/**
 * Manages document printing for the Player screen.
 * Supports PDF (custom PrintDocumentAdapter), IMAGE (PrintHelper), and TEXT (WebView adapter).
 *
 * Works for any resource type - local files, content:// URIs, SMB/SFTP/FTP, Google Drive,
 * Dropbox, OneDrive. All non-local paths are materialised to a local readable File via
 * [NetworkFileManager.prepareFileForRead]. That method returns either the original local
 * file, a copied content:// temp file (cacheDir), or a file from UnifiedFileCache - none of
 * which this class should delete: cacheDir is managed by the OS, UnifiedFileCache has its
 * own LRU eviction, and deleting a cached network file would invalidate the player's cache.
 */
class DocumentPrintManager(
    private val activity: PlayerActivity,
    private val mediaCapabilities: MediaCapabilities
) {

    private val printFallbackManager = PlayerPrintFallbackManager(activity)

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

        activity.lifecycleScope.launch {
            val file = prepareLocalFile(mediaFile) ?: return@launch
            if (activity.isFinishing || activity.isDestroyed) {
                Timber.w("DocumentPrintManager: activity finishing/destroyed after prepare, aborting")
                return@launch
            }
            val jobLabel = activity.getString(R.string.print_job_label, mediaFile.name)
            val sourceName = mediaFile.name
            when (mediaFile.type) {
                MediaType.PDF   -> printPdf(file, jobLabel, sourceName)
                MediaType.IMAGE -> printImage(file, jobLabel, sourceName)
                MediaType.TEXT  -> printText(file, jobLabel, sourceName)
                // S0301 Phase 05: Office documents print through the embedded viewer's own
                // WebView adapter (noLegal). The host returns false in market flavors / when
                // nothing is rendered, in which case we degrade to the standard fallback.
                MediaType.OFFICE_DOCUMENT -> {
                    if (!activity.officeDocumentViewerManager.print()) {
                        Timber.w("DocumentPrintManager: no internal Office print path, skipping ${mediaFile.name}")
                    }
                }
                else -> Timber.w("DocumentPrintManager: unsupported type for print: ${mediaFile.type}")
            }
        }
    }

    /**
     * Resolve [PrintManager] lazily, right before dispatching the job. Android requires
     * PrintManager.print() to be invoked from an instance whose context is an Activity
     * (checked via `mContext instanceof Activity`). On some OEM builds / x86 emulators this
     * check fails when the manager is obtained via application context, so we:
     *   1) fetch via `activity.getSystemService(Context.PRINT_SERVICE)` - guarantees Activity-backed instance;
     *   2) post the actual print() onto [Activity.runOnUiThread] to guarantee a fresh main-tick;
     *   3) wrap in try/catch to degrade gracefully instead of crashing the process.
     */
    private fun dispatchPrint(
        adapter: PrintDocumentAdapter,
        jobLabel: String,
        sourceFile: File,
        sourceName: String,
        mimeType: String
    ) {
        // Direct Activity.getSystemService guarantees the returned PrintManager holds an
        // Activity context - required by PrintManager.print() internal check on API 26–27.
        // ContextCompat.getSystemService can return an app-context-backed instance on some
        // x86 emulator builds, causing IllegalStateException: "Can print only from an activity".
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (pm == null) {
            Timber.e("DocumentPrintManager: PRINT_SERVICE returned null")
            showPrintFailedSnackbar(sourceFile, sourceName, mimeType)
            return
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            try {
                Timber.d(
                    "DocumentPrintManager: dispatching '$jobLabel' via ${pm.javaClass.name}, " +
                        "ctx=${activity.javaClass.name}"
                )
                pm.print(
                    jobLabel,
                    adapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            } catch (e: IllegalStateException) {
                // Happens on devices where PrintManager's internal context is not an Activity
                Timber.e(e, "DocumentPrintManager: PrintManager.print rejected the call (${e.javaClass.simpleName}: ${e.message})")
                showPrintFailedSnackbar(sourceFile, sourceName, mimeType)
            } catch (e: Exception) {
                Timber.e(e, "DocumentPrintManager: unexpected error dispatching print job (${e.javaClass.simpleName}: ${e.message})")
                showPrintFailedSnackbar(sourceFile, sourceName, mimeType)
            }
        }
    }

    // ─── Resolution: any resource → local File ────────────────────────────────

    /**
     * Resolves [mediaFile] to a local readable [File]. For local paths returns the file as-is;
     * for content://, SMB/SFTP/FTP, and cloud resources downloads (or retrieves from cache)
     * via [NetworkFileManager]. Returns null and shows a snackbar on failure or size overflow.
     */
    private suspend fun prepareLocalFile(mediaFile: MediaFile): File? {
        val file = try {
            withContext(Dispatchers.IO) {
                activity.networkFileManager.prepareFileForRead(mediaFile)
            }
        } catch (e: Exception) {
            Timber.e(e, "DocumentPrintManager: failed to prepare file for print: ${mediaFile.path}")
            showSnackbar(activity.getString(R.string.error_print_download_failed))
            return null
        }

        if (!file.exists() || file.length() == 0L) {
            Timber.e("DocumentPrintManager: prepared file is missing or empty: ${file.absolutePath}")
            showSnackbar(activity.getString(R.string.error_print_download_failed))
            return null
        }

        if (file.length() > MAX_PRINT_FILE_SIZE_BYTES) {
            Timber.w(
                "DocumentPrintManager: file too large for print " +
                    "(${file.length()} bytes > $MAX_PRINT_FILE_SIZE_BYTES)"
            )
            showSnackbar(activity.getString(R.string.error_print_file_too_large))
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
        dispatchPrint(
            PdfPrintDocumentAdapter(sourceFile = file, jobLabel = jobLabel),
            jobLabel,
            sourceFile = file,
            sourceName = sourceName,
            mimeType = "application/pdf"
        )
    }

    // ─── IMAGE ────────────────────────────────────────────────────────────────

    private suspend fun printImage(file: File, jobLabel: String, sourceName: String) {
        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                Timber.e(e, "DocumentPrintManager: error decoding image bitmap")
                null
            }
        }
        if (bitmap == null) {
            Timber.e("DocumentPrintManager: BitmapFactory returned null for ${file.absolutePath}")
            showSnackbar(activity.getString(R.string.error_print_download_failed))
            return
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            try {
                PrintHelper(activity).apply {
                    scaleMode = PrintHelper.SCALE_MODE_FIT
                }.printBitmap(jobLabel, bitmap)
            } catch (e: Exception) {
                Timber.e(e, "DocumentPrintManager: PrintHelper.printBitmap failed (${e.javaClass.simpleName}: ${e.message})")
                showPrintFailedSnackbar(file, sourceName, "image/*")
            }
        }
    }

    // ─── TEXT ─────────────────────────────────────────────────────────────────

    private suspend fun printText(file: File, jobLabel: String, sourceName: String) {
        val text: String? = withContext(Dispatchers.IO) {
            try {
                file.readText()
            } catch (e: Exception) {
                Timber.e(e, "DocumentPrintManager: error reading text file")
                null
            }
        }
        if (text == null) {
            showSnackbar(activity.getString(R.string.error_print_download_failed))
            return
        }

        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // WebView must be created and loaded on the main thread. Creating it can fail when the
        // system WebView package is disabled or mid-update - surface that as a print error
        // instead of letting it crash the coroutine.
        val webView = try {
            WebView(activity)
        } catch (e: Exception) {
            Timber.e(e, "DocumentPrintManager: WebView unavailable for text print (${e.javaClass.simpleName}: ${e.message})")
            showSnackbar(activity.getString(R.string.error_print_unavailable))
            return
        }
        webView.loadData("<html><body><pre>$escaped</pre></body></html>", "text/html", "UTF-8")
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                dispatchPrint(
                    webView.createPrintDocumentAdapter(jobLabel),
                    jobLabel,
                    sourceFile = file,
                    sourceName = sourceName,
                    mimeType = "text/plain"
                )
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun showSnackbar(message: String) {
        Snackbar.make(activity.activityBinding.root, message, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Shown when the system print dialog could not be opened: offers the file to the system share
     * chooser as a fallback (S0145) and only falls back to the plain "print unavailable" notice if
     * even that fails.
     */
    private fun showPrintFailedSnackbar(sourceFile: File, sourceName: String, mimeType: String) {
        if (printFallbackManager.shareForPrint(sourceFile, sourceName, mimeType)) {
            showSnackbar(activity.getString(R.string.print_fallback_to_share))
        } else {
            showSnackbar(activity.getString(R.string.error_print_unavailable))
        }
    }

    // ─── PDF PrintDocumentAdapter ─────────────────────────────────────────────

    /** Streams a PDF from a local [sourceFile] to the print spooler. */
    private class PdfPrintDocumentAdapter(
        private val sourceFile: File,
        private val jobLabel: String
    ) : PrintDocumentAdapter() {

        private var copyJob: Thread? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(jobLabel)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            copyJob = Thread {
                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null
                try {
                    inputStream = FileInputStream(sourceFile)
                    outputStream = FileOutputStream(destination.fileDescriptor)
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (cancellationSignal.isCanceled) {
                            callback.onWriteCancelled()
                            return@Thread
                        }
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: IOException) {
                    Timber.e(e, "PdfPrintDocumentAdapter: error writing PDF")
                    // Android surfaces this text directly in the print UI, so avoid leaking raw I/O details.
                    callback.onWriteFailed(null)
                } finally {
                    try { inputStream?.close() } catch (_: Exception) {}
                    try { outputStream?.close() } catch (_: Exception) {}
                }
            }
            copyJob!!.start()
        }

        override fun onFinish() {
            copyJob?.interrupt()
        }
    }
}
