package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import androidx.print.PrintHelper
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val MAX_PRINT_FILE_SIZE_BYTES = 30L * 1024 * 1024 // 30 MB

/**
 * Manages document printing for the Player screen.
 * Supports PDF (custom PrintDocumentAdapter), IMAGE (PrintHelper), and TEXT (WebView adapter).
 * Network files (SMB/SFTP/FTP/Cloud) are cached locally before printing and cleaned up after.
 *
 * Follows the same pattern as [PlayerShareManager]: instantiated directly in PlayerActivity.
 */
class DocumentPrintManager(
    private val activity: PlayerActivity
) {

    /**
     * Entry point: print the currently open file.
     * Silently returns if BuildConfig.SUPPORT_DOCUMENTS is false (lite/photos flavors).
     */
    fun printCurrentFile(uri: Uri, mediaType: MediaType, fileName: String) {
        if (!BuildConfig.SUPPORT_DOCUMENTS && mediaType != MediaType.IMAGE) {
            Timber.w("DocumentPrintManager: SUPPORT_DOCUMENTS=false, skipping print for $mediaType")
            return
        }

        val pm = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (pm == null) {
            showSnackbar(activity.getString(R.string.error_print_unavailable))
            return
        }

        Timber.i("DocumentPrintManager: starting print, type=$mediaType, file=$fileName")

        when (mediaType) {
            MediaType.PDF  -> printPdf(pm, uri, fileName)
            MediaType.IMAGE -> printImage(uri, fileName)
            MediaType.TEXT -> printText(pm, uri, fileName)
            else -> {
                Timber.w("DocumentPrintManager: unsupported type for print: $mediaType")
            }
        }
    }

    // ─── PDF ──────────────────────────────────────────────────────────────────

    private fun printPdf(pm: PrintManager, uri: Uri, fileName: String) {
        activity.lifecycleScope.launch {
            val resolvedUri = resolveToLocalUri(uri, fileName) ?: return@launch
            val adapter = PdfPrintDocumentAdapter(
                context = activity,
                sourceUri = resolvedUri,
                jobLabel = activity.getString(R.string.print_job_label, fileName),
                tempFileToDelete = if (resolvedUri != uri) File(resolvedUri.path!!) else null
            )
            pm.print(
                activity.getString(R.string.print_job_label, fileName),
                adapter,
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
            )
        }
    }

    // ─── IMAGE ────────────────────────────────────────────────────────────────

    private fun printImage(uri: Uri, fileName: String) {
        activity.lifecycleScope.launch {
            val resolvedUri = resolveToLocalUri(uri, fileName) ?: return@launch
            val bitmap: Bitmap? = withContext(Dispatchers.IO) {
                try {
                    activity.contentResolver.openInputStream(resolvedUri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "DocumentPrintManager: error decoding image bitmap")
                    null
                }
            }
            if (bitmap == null) {
                showSnackbar(activity.getString(R.string.error_print_download_failed))
                return@launch
            }
            val helper = PrintHelper(activity).apply {
                scaleMode = PrintHelper.SCALE_MODE_FIT
            }
            helper.printBitmap(activity.getString(R.string.print_job_label, fileName), bitmap)
        }
    }

    // ─── TEXT ─────────────────────────────────────────────────────────────────

    private fun printText(pm: PrintManager, uri: Uri, fileName: String) {
        activity.lifecycleScope.launch {
            val resolvedUri = resolveToLocalUri(uri, fileName) ?: return@launch
            val text: String? = withContext(Dispatchers.IO) {
                try {
                    activity.contentResolver.openInputStream(resolvedUri)?.bufferedReader()?.readText()
                } catch (e: Exception) {
                    Timber.e(e, "DocumentPrintManager: error reading text file")
                    null
                }
            }
            if (text == null) {
                showSnackbar(activity.getString(R.string.error_print_download_failed))
                return@launch
            }

            // WebView must be created on the main thread
            val webView = WebView(activity)
            // Load plain text wrapped in <pre> to preserve whitespace/newlines
            val escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            webView.loadData("<html><body><pre>$escaped</pre></body></html>", "text/html", "UTF-8")

            // Wait for WebView to finish loading before handing adapter to PrintManager
            webView.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val tempFile = if (resolvedUri != uri) File(resolvedUri.path!!) else null
                    val adapter = webView.createPrintDocumentAdapter(
                        activity.getString(R.string.print_job_label, fileName)
                    )
                    // Wrap to clean up temp file on finish
                    val wrappedAdapter = if (tempFile != null) {
                        CleanupWrappedPrintAdapter(adapter, tempFile)
                    } else {
                        adapter
                    }
                    pm.print(
                        activity.getString(R.string.print_job_label, fileName),
                        wrappedAdapter,
                        PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .build()
                    )
                }
            }
        }
    }

    // ─── Network / Cloud file resolution ──────────────────────────────────────

    /**
     * If the [uri] is accessible by PrintManager (content:// or file:// pointing to a local file),
     * returns it as-is. Otherwise downloads it to cacheDir and returns a file:// Uri.
     * Returns null and shows an error Snackbar if download fails or file exceeds size limit.
     */
    private suspend fun resolveToLocalUri(uri: Uri, fileName: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = activity.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    inputStream.close()
                    // Stream opened OK → uri is already accessible
                    return@withContext uri
                }
            } catch (_: Exception) {
                // Fall through to download
            }

            // Need to download
            Timber.d("DocumentPrintManager: uri not directly accessible, downloading for print: $uri")
            downloadToCache(uri, fileName)
        }
    }

    private suspend fun downloadToCache(uri: Uri, fileName: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = File(activity.cacheDir, "print_tmp_${System.currentTimeMillis()}_$fileName")
                var inputStream: InputStream? = null

                // Attempt 1: ContentResolver (handles content:// from all sources incl. SAF)
                try {
                    inputStream = activity.contentResolver.openInputStream(uri)
                } catch (_: Exception) {}

                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        showSnackbar(activity.getString(R.string.error_print_download_failed))
                    }
                    return@withContext null
                }

                inputStream.use { stream ->
                    // Check size limit
                    val bytes = stream.readBytes()
                    if (bytes.size > MAX_PRINT_FILE_SIZE_BYTES) {
                        withContext(Dispatchers.Main) {
                            showSnackbar(activity.getString(R.string.error_print_file_too_large))
                        }
                        return@withContext null
                    }
                    FileOutputStream(tempFile).use { out -> out.write(bytes) }
                }

                Timber.d("DocumentPrintManager: cached file for print -> ${tempFile.absolutePath}")
                Uri.fromFile(tempFile)
            } catch (e: Exception) {
                Timber.e(e, "DocumentPrintManager: failed to download file for print")
                withContext(Dispatchers.Main) {
                    showSnackbar(activity.getString(R.string.error_print_download_failed))
                }
                null
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun showSnackbar(message: String) {
        Snackbar.make(activity.activityBinding.root, message, Snackbar.LENGTH_LONG).show()
    }

    // ─── PDF PrintDocumentAdapter ─────────────────────────────────────────────

    /**
     * Custom [PrintDocumentAdapter] that reads a PDF from [sourceUri] and pipes it directly
     * to the print spooler. Closes all streams and optionally deletes [tempFileToDelete] in onFinish().
     */
    private class PdfPrintDocumentAdapter(
        private val context: Context,
        private val sourceUri: Uri,
        private val jobLabel: String,
        private val tempFileToDelete: File?
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
                    inputStream = context.contentResolver.openInputStream(sourceUri)
                        ?: throw IOException("Cannot open URI: $sourceUri")
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
                } catch (e: Exception) {
                    Timber.e(e, "PdfPrintDocumentAdapter: error writing PDF")
                    callback.onWriteFailed(e.message)
                } finally {
                    try { inputStream?.close() } catch (_: Exception) {}
                    try { outputStream?.close() } catch (_: Exception) {}
                }
            }
            copyJob!!.start()
        }

        override fun onFinish() {
            copyJob?.interrupt()
            tempFileToDelete?.let {
                try {
                    if (it.exists()) it.delete()
                    Timber.d("PdfPrintDocumentAdapter: deleted temp file ${it.name}")
                } catch (e: Exception) {
                    Timber.w(e, "PdfPrintDocumentAdapter: could not delete temp file")
                }
            }
        }
    }

    // ─── Cleanup wrapper for WebView text adapter ─────────────────────────────

    /**
     * Wraps a [PrintDocumentAdapter] to delete [tempFile] after printing is done.
     */
    private class CleanupWrappedPrintAdapter(
        private val delegate: PrintDocumentAdapter,
        private val tempFile: File
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) = delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) = delegate.onWrite(pages, destination, cancellationSignal, callback)

        override fun onFinish() {
            delegate.onFinish()
            try {
                if (tempFile.exists()) tempFile.delete()
                Timber.d("CleanupWrappedPrintAdapter: deleted temp file ${tempFile.name}")
            } catch (e: Exception) {
                Timber.w(e, "CleanupWrappedPrintAdapter: could not delete temp file")
            }
        }
    }
}
