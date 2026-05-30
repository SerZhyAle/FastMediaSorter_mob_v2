package com.sza.fastmediasorter.ui.player.helpers

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.sza.fastmediasorter.data.common.OfficeDocumentFamily
import com.sza.fastmediasorter.data.common.OfficeDocumentFamilyCatalog
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File

/**
 * noLegal embedded Office viewer (S0301 Phase 03).
 *
 * Owns the in-app, read-only render lifecycle for the Word / spreadsheet / presentation
 * families. The actual document-to-HTML conversion is delegated to
 * [OfficeDocumentEngineBridge]; link safety is enforced by [OfficeDocumentHyperlinkPolicy].
 * The viewer reuses the shared `officeDocumentViewerContainer` surface in the unified player
 * layout, mirroring how PDF/EPUB managers own their own surfaces.
 *
 * Read-only by contract: JavaScript is disabled, no editing affordances exist, and the
 * WebView blocks active content while letting ordinary hyperlinks open via the system.
 */
@SuppressLint("SetJavaScriptEnabled")
class OfficeDocumentViewerManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val coroutineScope: CoroutineScope,
    private val callback: OfficeDocumentViewerHost.Callback,
) : OfficeDocumentViewerHost {

    private val engineBridge = OfficeDocumentEngineBridge()
    private val hyperlinkPolicy = OfficeDocumentHyperlinkPolicy()
    // S0301 Phase 04: family-specific render delegates keep sheet/slide navigation models and
    // read-only state isolated from the Word-family flow. The Word family renders directly
    // through the engine bridge; spreadsheets and presentations go through their delegates.
    private val spreadsheetDelegate = OfficeSpreadsheetViewerDelegate(engineBridge)
    private val presentationDelegate = OfficePresentationViewerDelegate(engineBridge)
    // S0301 Phase 05: read-only print parity - prints the rendered WebView through the framework
    // PrintManager instead of routing Office files through the PDF print path.
    private val printAdapter = OfficeDocumentPrintAdapter()
    private val container get() = binding.officeDocumentViewerContainer

    private var webView: WebView? = null
    private var fullscreen = false
    private var renderedPlainText: String? = null

    override val isActive: Boolean
        get() = webView != null && container.isVisible

    override fun isInFullscreenMode(): Boolean = fullscreen

    override fun open(mediaFile: MediaFile, file: File): Boolean {
        val ext = file.extension.lowercase()
        val family = OfficeDocumentFamilyCatalog.extensionToFamily[ext]
        if (family == null || family !in OfficeDocumentFamilyCatalog.supportedFamilies) {
            return false
        }
        // S0301 Phase 03: explicit viewer session for the Word-family open path. The file is
        // already materialized to local storage by the caller (S0299 prepareFileForRead),
        // so the engine only reads it - no temp ownership beyond the shared cache contract.
        val session = OfficeDocumentViewerSession(
            mediaFile = mediaFile,
            outcome = OfficeDocumentViewerOutcome.DISPLAY_INTERNALLY,
        )
        coroutineScope.launch {
            // S0301 Phase 04: switch the render model by family so sheet/slide documents use
            // their dedicated delegates while the Word family stays on the page-oriented path.
            val html = withContext(Dispatchers.IO) { renderByFamily(family, file) }
            if (html == null) {
                // Parse failure / unsupported variant discovered after open() committed - do not
                // fail silently: hide the surface and route to the explicit external fallback.
                showExplicitFallbackDialog(session.mediaFile)
                return@launch
            }
            renderHtml(html)
        }
        return true
    }

    /** Dispatch rendering to the family-specific delegate (S0301 Phase 04). */
    private fun renderByFamily(family: OfficeDocumentFamily, file: File): String? = when (family) {
        OfficeDocumentFamily.SPREADSHEET -> spreadsheetDelegate.render(file)
        OfficeDocumentFamily.PRESENTATION -> presentationDelegate.render(file)
        OfficeDocumentFamily.WORD -> engineBridge.renderToHtml(file, family)
    }

    /**
     * Hide the in-app surface and ask the player to open [mediaFile] externally (S0301 Phase 04).
     * Used when the engine cannot render a supported family (legacy binary or parse failure).
     */
    private fun showExplicitFallbackDialog(mediaFile: MediaFile) {
        Timber.w("OfficeDocumentViewerManager: no internal render for ${mediaFile.name}, routing to external fallback")
        release()
        callback.onRequireExternalFallback(mediaFile)
    }

    private fun renderHtml(html: String) {
        val view = webView ?: createWebView().also { webView = it }
        renderedPlainText = Jsoup.parse(html).text().trim().takeIf { it.isNotBlank() }
        container.isVisible = true
        view.isVisible = true
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun createWebView(): WebView {
        val view = WebView(container.context)
        view.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString()
                if (hyperlinkPolicy.blockActiveContent(url)) {
                    Timber.w("OfficeDocumentViewerManager: blocked active-content link")
                    return true
                }
                if (hyperlinkPolicy.allowOrdinaryLinks(url) && url != null) {
                    runCatching {
                        container.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure { Timber.w(it, "OfficeDocumentViewerManager: failed to open link") }
                    return true
                }
                return true
            }
        }
        container.addView(view)
        return view
    }

    override fun release() {
        webView?.let { view ->
            view.loadUrl("about:blank")
            container.removeView(view)
            view.destroy()
        }
        webView = null
        renderedPlainText = null
        container.isVisible = false
        fullscreen = false
    }

    override fun print(): Boolean {
        // S0301 Phase 05: only print when something is actually rendered; otherwise let the
        // caller fall back. The print job mirrors the read-only on-screen content exactly.
        val view = webView ?: return false
        return printAdapter.print(view, jobName = "FastMediaSorter Office")
    }

    override fun extractPlainText(): String? = renderedPlainText
}
