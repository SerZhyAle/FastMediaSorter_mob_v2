package com.sza.fastmediasorter.ui.player.print

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.print.PrintHelper
import com.sza.fastmediasorter.ui.player.helpers.PrintShareFallbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Internal print trampoline for document/text jobs (S0613).
 *
 * Intentionally extends AppCompatActivity directly - BaseActivity wraps attachBaseContext() with
 * createConfigurationContext(), and Samsung/One UI can then reject PrintManager.print() with
 * "Can print only from an activity". This transparent host keeps the print dispatch on a plain
 * Activity outer context while preserving the existing share-for-print fallback.
 */
class PrintDispatchActivity : AppCompatActivity() {

    private var request: PrintRequest? = null
    private var dispatchStarted = false
    private var awaitingExternalReturn = false
    private var returnedFromExternalUi = false
    private var textJobDispatched = false
    private var textWebView: WebView? = null

    private val printFallbackManager by lazy { PrintShareFallbackManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = PrintRequest.fromIntent(intent)
        if (request == null) {
            finish()
            return
        }
        dispatchStarted = savedInstanceState?.getBoolean(STATE_DISPATCH_STARTED) ?: false
        awaitingExternalReturn = savedInstanceState?.getBoolean(STATE_AWAITING_RETURN) ?: false
        returnedFromExternalUi = savedInstanceState?.getBoolean(STATE_RETURNED_FROM_EXTERNAL_UI) ?: false
        textJobDispatched = savedInstanceState?.getBoolean(STATE_TEXT_JOB_DISPATCHED) ?: false
    }

    override fun onPostResume() {
        super.onPostResume()
        if (returnedFromExternalUi) {
            finish()
            return
        }
        if (!dispatchStarted) {
            dispatchStarted = true
            when (request?.mode) {
                PrintMode.PDF -> dispatchPdf()
                PrintMode.TEXT -> dispatchText()
                PrintMode.IMAGE -> dispatchImage()
                null -> finish()
            }
        }
    }

    override fun onStop() {
        if (awaitingExternalReturn) {
            returnedFromExternalUi = true
        }
        super.onStop()
    }

    override fun onDestroy() {
        textWebView?.destroy()
        textWebView = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISPATCH_STARTED, dispatchStarted)
        outState.putBoolean(STATE_AWAITING_RETURN, awaitingExternalReturn)
        outState.putBoolean(STATE_RETURNED_FROM_EXTERNAL_UI, returnedFromExternalUi)
        outState.putBoolean(STATE_TEXT_JOB_DISPATCHED, textJobDispatched)
    }

    // Guard-clause early returns on the unavailable/fallback paths - ReturnCount is intentional.
    @Suppress("ReturnCount")
    private fun dispatchPdf() {
        val current = request ?: return finish()
        val file = File(current.filePath)
        if (!file.exists()) {
            showUnavailableAndFinish(current)
            return
        }
        val printManager = getSystemService(PrintManager::class.java)
        if (printManager == null) {
            fallbackToShareOrFail(file, current, "application/pdf")
            return
        }
        runCatching {
            printManager.print(
                current.jobLabel,
                PdfPrintDocumentAdapter(sourceFile = file, jobLabel = current.jobLabel),
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
            )
        }.onSuccess {
            awaitingExternalReturn = true
        }.onFailure { error ->
            Timber.e(
                error,
                "PrintDispatchActivity: PDF dispatch failed (${error.javaClass.simpleName}: ${error.message})"
            )
            fallbackToShareOrFail(file, current, "application/pdf")
        }
    }

    // Guard-clause early return on the missing-file path - ReturnCount is intentional.
    @Suppress("ReturnCount")
    private fun dispatchText() {
        val current = request ?: return finish()
        val file = File(current.filePath)
        if (!file.exists()) {
            showUnavailableAndFinish(current)
            return
        }
        // S1195: the read and the escaping both scale with file size and used to run on the main
        // thread straight out of onPostResume(). lifecycleScope binds the work to the Activity, so
        // a teardown mid-read cancels it instead of resuming into a dead WebView.
        lifecycleScope.launch {
            val escaped = withContext(Dispatchers.IO) { readAndEscapeForPrint(file) }
            if (escaped == null) {
                fallbackToShareOrFail(file, current, "text/plain")
                return@launch
            }
            renderTextForPrint(file, current, escaped)
        }
    }

    private fun readAndEscapeForPrint(file: File): String? = runCatching { file.readText() }
        .onFailure { error ->
            Timber.e(
                error,
                "PrintDispatchActivity: text read failed (${error.javaClass.simpleName}: ${error.message})"
            )
        }
        .getOrNull()
        ?.replace("&", "&amp;")
        ?.replace("<", "&lt;")
        ?.replace(">", "&gt;")

    // Guard-clause early return (fallback path); WebView(this) can throw on broken WebView installs.
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun renderTextForPrint(file: File, current: PrintRequest, escaped: String) {
        val webView = try {
            WebView(this)
        } catch (error: Exception) {
            Timber.e(
                error,
                "PrintDispatchActivity: WebView unavailable for text print"
            )
            fallbackToShareOrFail(file, current, "text/plain")
            return
        }
        textWebView = webView
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (textJobDispatched) return
                textJobDispatched = true
                dispatchTextAdapter(file, current, webView)
            }
        }
        webView.loadData("<html><body><pre>$escaped</pre></body></html>", "text/html", "UTF-8")
    }

    private fun dispatchTextAdapter(file: File, current: PrintRequest, webView: WebView) {
        val printManager = getSystemService(PrintManager::class.java)
        if (printManager == null) {
            fallbackToShareOrFail(file, current, "text/plain")
            return
        }
        runCatching {
            printManager.print(
                current.jobLabel,
                webView.createPrintDocumentAdapter(current.jobLabel),
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
            )
        }.onSuccess {
            awaitingExternalReturn = true
        }.onFailure { error ->
            Timber.e(
                error,
                "PrintDispatchActivity: text dispatch failed (${error.javaClass.simpleName}: ${error.message})"
            )
            fallbackToShareOrFail(file, current, "text/plain")
        }
    }

    // S0610/S0919: image print is routed here so One UI reaches PrintManager.print() from a clean
    // Activity context (BaseActivity's createConfigurationContext wrapper trips "Can print only from
    // an activity").
    // Guard-clause early return on the missing-file path - ReturnCount is intentional.
    @Suppress("ReturnCount")
    private fun dispatchImage() {
        val current = request ?: return finish()
        val file = File(current.filePath)
        if (!file.exists()) {
            showUnavailableAndFinish(current)
            return
        }
        // S1195: decodeFile reads and decodes the whole image; from onPostResume() that was a
        // main-thread stall proportional to file size. Same defect as the text path - the old
        // MainThreadIo rule missed it only because decodeFile is not one of the names it watches.
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmapForPrint(file) }
            if (bitmap == null) {
                fallbackToShareOrFail(file, current, "image/*")
                return@launch
            }
            printBitmapOrFallback(file, current, bitmap)
        }
    }

    // BitmapFactory.decodeFile can throw OOM/format errors on hostile files.
    @Suppress("TooGenericExceptionCaught")
    private fun decodeBitmapForPrint(file: File): Bitmap? = try {
        BitmapFactory.decodeFile(file.absolutePath)
    } catch (error: Exception) {
        Timber.e(error, "PrintDispatchActivity: image decode failed (${error.javaClass.simpleName})")
        null
    }

    private fun printBitmapOrFallback(file: File, current: PrintRequest, bitmap: Bitmap) {
        runCatching {
            PrintHelper(this).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }
                .printBitmap(current.jobLabel, bitmap)
        }.onSuccess {
            awaitingExternalReturn = true
        }.onFailure { error ->
            Timber.e(
                error,
                "PrintDispatchActivity: image dispatch failed (${error.javaClass.simpleName}: ${error.message})"
            )
            fallbackToShareOrFail(file, current, "image/*")
        }
    }

    private fun fallbackToShareOrFail(file: File, current: PrintRequest, mimeType: String) {
        if (printFallbackManager.shareForPrint(file, current.displayName, mimeType, current.chooserTitle)) {
            android.widget.Toast.makeText(this, current.fallbackMessage, android.widget.Toast.LENGTH_LONG).show()
            awaitingExternalReturn = true
        } else {
            showUnavailableAndFinish(current)
        }
    }

    private fun showUnavailableAndFinish(current: PrintRequest) {
        android.widget.Toast.makeText(this, current.unavailableMessage, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }

    private enum class PrintMode {
        PDF,
        TEXT,
        IMAGE,
    }

    private data class PrintRequest(
        val mode: PrintMode,
        val filePath: String,
        val jobLabel: String,
        val displayName: String,
        val chooserTitle: String,
        val fallbackMessage: String,
        val unavailableMessage: String,
    ) {
        companion object {
            // One guarded return per required extra - ReturnCount is intentional for this parser.
            @Suppress("ReturnCount")
            fun fromIntent(intent: android.content.Intent?): PrintRequest? {
                intent ?: return null
                val mode = intent.getStringExtra(EXTRA_MODE)
                    ?.let { value -> PrintMode.entries.firstOrNull { it.name == value } }
                    ?: return null
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)?.takeIf { it.isNotBlank() } ?: return null
                val jobLabel = intent.getStringExtra(EXTRA_JOB_LABEL)?.takeIf { it.isNotBlank() } ?: return null
                val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: return null
                val chooserTitle = intent.getStringExtra(EXTRA_CHOOSER_TITLE)?.takeIf { it.isNotBlank() } ?: return null
                val fallbackMessage = intent.getStringExtra(EXTRA_FALLBACK_MESSAGE)
                    ?.takeIf { it.isNotBlank() } ?: return null
                val unavailableMessage = intent.getStringExtra(EXTRA_UNAVAILABLE_MESSAGE)
                    ?.takeIf { it.isNotBlank() } ?: return null
                return PrintRequest(
                    mode = mode,
                    filePath = filePath,
                    jobLabel = jobLabel,
                    displayName = displayName,
                    chooserTitle = chooserTitle,
                    fallbackMessage = fallbackMessage,
                    unavailableMessage = unavailableMessage,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_FILE_PATH = "extra_file_path"
        private const val EXTRA_JOB_LABEL = "extra_job_label"
        private const val EXTRA_DISPLAY_NAME = "extra_display_name"
        private const val EXTRA_CHOOSER_TITLE = "extra_chooser_title"
        private const val EXTRA_FALLBACK_MESSAGE = "extra_fallback_message"
        private const val EXTRA_UNAVAILABLE_MESSAGE = "extra_unavailable_message"

        private const val STATE_DISPATCH_STARTED = "state_dispatch_started"
        private const val STATE_AWAITING_RETURN = "state_awaiting_return"
        private const val STATE_RETURNED_FROM_EXTERNAL_UI = "state_returned_from_external_ui"
        private const val STATE_TEXT_JOB_DISPATCHED = "state_text_job_dispatched"

        fun startPdf(
            context: android.content.Context,
            file: File,
            jobLabel: String,
            displayName: String,
            chooserTitle: String,
            fallbackMessage: String,
            unavailableMessage: String,
        ): Boolean = start(
            context = context,
            mode = PrintMode.PDF,
            file = file,
            jobLabel = jobLabel,
            displayName = displayName,
            chooserTitle = chooserTitle,
            fallbackMessage = fallbackMessage,
            unavailableMessage = unavailableMessage,
        )

        fun startText(
            context: android.content.Context,
            file: File,
            jobLabel: String,
            displayName: String,
            chooserTitle: String,
            fallbackMessage: String,
            unavailableMessage: String,
        ): Boolean = start(
            context = context,
            mode = PrintMode.TEXT,
            file = file,
            jobLabel = jobLabel,
            displayName = displayName,
            chooserTitle = chooserTitle,
            fallbackMessage = fallbackMessage,
            unavailableMessage = unavailableMessage,
        )

        // S0919: [file] must be a decodable image on disk (the standalone host stages the on-screen
        // bitmap to a private temp PNG; the document host passes its materialised image file).
        fun startImage(
            context: android.content.Context,
            file: File,
            jobLabel: String,
            displayName: String,
            chooserTitle: String,
            fallbackMessage: String,
            unavailableMessage: String,
        ): Boolean = start(
            context = context,
            mode = PrintMode.IMAGE,
            file = file,
            jobLabel = jobLabel,
            displayName = displayName,
            chooserTitle = chooserTitle,
            fallbackMessage = fallbackMessage,
            unavailableMessage = unavailableMessage,
        )

        // Params mirror the intent extras 1:1; a param object would only shuffle the same fields.
        @Suppress("LongParameterList")
        private fun start(
            context: android.content.Context,
            mode: PrintMode,
            file: File,
            jobLabel: String,
            displayName: String,
            chooserTitle: String,
            fallbackMessage: String,
            unavailableMessage: String,
        ): Boolean = runCatching {
            context.startActivity(
                android.content.Intent(context, PrintDispatchActivity::class.java).apply {
                    putExtra(EXTRA_MODE, mode.name)
                    putExtra(EXTRA_FILE_PATH, file.absolutePath)
                    putExtra(EXTRA_JOB_LABEL, jobLabel)
                    putExtra(EXTRA_DISPLAY_NAME, displayName)
                    putExtra(EXTRA_CHOOSER_TITLE, chooserTitle)
                    putExtra(EXTRA_FALLBACK_MESSAGE, fallbackMessage)
                    putExtra(EXTRA_UNAVAILABLE_MESSAGE, unavailableMessage)
                }
            )
            true
        }.onFailure { error ->
            Timber.e(
                error,
                "PrintDispatchActivity: failed to launch trampoline (${error.javaClass.simpleName}: ${error.message})"
            )
        }.getOrDefault(false)
    }

    /** Streams a PDF from a local [sourceFile] to the print spooler. */
    private class PdfPrintDocumentAdapter(
        private val sourceFile: File,
        private val jobLabel: String,
    ) : PrintDocumentAdapter() {

        // S0828: structured, cancellable copy scope replaces a raw Thread whose interrupt() could not
        // reliably unblock a stuck FileInputStream.read() on some OEMs.
        private val adapterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Held so cancellation can close it: closing the source stream unblocks a blocked read() where
        // Thread.interrupt() would not (interruptibility of blocking file I/O is OEM-dependent).
        @Volatile private var activeInput: InputStream? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?,
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
            callback: WriteResultCallback,
        ) {
            // Proactively abort when the platform signals cancel; closing the stream unblocks read().
            cancellationSignal.setOnCancelListener { cancelActiveCopy() }
            adapterScope.launch {
                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null
                try {
                    inputStream = FileInputStream(sourceFile).also { activeInput = it }
                    outputStream = FileOutputStream(destination.fileDescriptor)
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (cancellationSignal.isCanceled) {
                            callback.onWriteCancelled()
                            return@launch
                        }
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (error: IOException) {
                    // A cancel closes activeInput mid-read, surfacing here - report cancelled, not failed.
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                    } else {
                        Timber.e(error, "PrintDispatchActivity: PDF spool write failed")
                        callback.onWriteFailed(null)
                    }
                } finally {
                    activeInput = null
                    try { inputStream?.close() } catch (_: IOException) {}
                    try { outputStream?.close() } catch (_: IOException) {}
                }
            }
        }

        override fun onFinish() {
            cancelActiveCopy()
            adapterScope.cancel()
        }

        /** Unblock a stuck copy by closing the source stream, then let the coroutine settle. */
        private fun cancelActiveCopy() {
            activeInput?.let { runCatching { it.close() } }
        }

        companion object {
            private const val COPY_BUFFER_BYTES = 64 * 1024
        }
    }
}
