package com.sza.fastmediasorter.ui.player.print

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
import com.sza.fastmediasorter.ui.player.helpers.PrintShareFallbackManager
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

    private fun dispatchText() {
        val current = request ?: return finish()
        val file = File(current.filePath)
        if (!file.exists()) {
            showUnavailableAndFinish(current)
            return
        }
        val rawText = runCatching { file.readText() }
            .onFailure { error ->
                Timber.e(
                    error,
                    "PrintDispatchActivity: text read failed (${error.javaClass.simpleName}: ${error.message})"
                )
            }
            .getOrNull()
        if (rawText == null) {
            fallbackToShareOrFail(file, current, "text/plain")
            return
        }
        val escaped = rawText
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val webView = try {
            WebView(this)
        } catch (error: Exception) {
            Timber.e(
                error,
                "PrintDispatchActivity: WebView unavailable for text print (${error.javaClass.simpleName}: ${error.message})"
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
            fun fromIntent(intent: android.content.Intent?): PrintRequest? {
                intent ?: return null
                val mode = intent.getStringExtra(EXTRA_MODE)
                    ?.let { value -> PrintMode.entries.firstOrNull { it.name == value } }
                    ?: return null
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)?.takeIf { it.isNotBlank() } ?: return null
                val jobLabel = intent.getStringExtra(EXTRA_JOB_LABEL)?.takeIf { it.isNotBlank() } ?: return null
                val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: return null
                val chooserTitle = intent.getStringExtra(EXTRA_CHOOSER_TITLE)?.takeIf { it.isNotBlank() } ?: return null
                val fallbackMessage = intent.getStringExtra(EXTRA_FALLBACK_MESSAGE)?.takeIf { it.isNotBlank() } ?: return null
                val unavailableMessage = intent.getStringExtra(EXTRA_UNAVAILABLE_MESSAGE)?.takeIf { it.isNotBlank() } ?: return null
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

        private var copyJob: Thread? = null

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
                } catch (error: IOException) {
                    Timber.e(error, "PrintDispatchActivity: PDF spool write failed")
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
