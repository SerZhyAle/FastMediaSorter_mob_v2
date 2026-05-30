package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.MediaFile
import java.io.File

/**
 * Flavor-safe lifecycle handle for the embedded Office viewer (S0301 Phase 03).
 *
 * The decision of *whether* to view internally is owned by [OfficeDocumentViewerProvider];
 * this host owns the actual in-app render lifecycle when that decision is
 * [OfficeDocumentViewerOutcome.DISPLAY_INTERNALLY].
 *
 * Market flavors (standard / legacy / vr / photos / lite) bind [NoOpOfficeDocumentViewerHost]
 * so no Office rendering code is linked into those builds. The noLegal flavor binds the real
 * engine-backed manager. Keeping the concrete manager out of `src/main` avoids leaking
 * noLegal-only code into market builds and removes any need for `BuildConfig.IS_*` checks.
 */
interface OfficeDocumentViewerHost {

    /** True when an Office document is currently rendered in the in-app viewer surface. */
    val isActive: Boolean

    /** True when the viewer is in immersive/fullscreen mode (mirrors PDF/EPUB managers). */
    fun isInFullscreenMode(): Boolean

    /**
     * Render the already-materialized local [file] for [mediaFile] in the in-app surface.
     *
    * Read-only: the viewer never edits the document. Returns true when rendering started
    * successfully; false signals the caller to show the explicit Office fallback dialog.
     */
    fun open(mediaFile: MediaFile, file: File): Boolean

    /** Hide the viewer surface and release any WebView / engine resources. Idempotent. */
    fun release()

    /**
     * Print the currently rendered Office document via the embedded viewer's own adapter
     * (S0301 Phase 05). Read-only: prints exactly what the in-app viewer shows. Returns true
     * when a print job was dispatched; false signals the caller that no internal print path is
     * available (e.g. nothing is rendered, or a market flavor with the no-op host).
     */
    fun print(): Boolean

    /**
     * Return the rendered document text for OCR/translation parity (S0301 Phase 05).
     * Market no-op hosts and empty renders return null so callers can hide unavailable actions.
     */
    fun extractPlainText(): String?

    /** UI callbacks routed back to the hosting player (errors, fullscreen transitions). */
    interface Callback {
        fun showError(message: String)
        fun onEnterFullscreenMode()
        fun onExitFullscreenMode()

        /**
         * The engine could not render [mediaFile] internally (legacy binary format or a parse
         * failure discovered after rendering started). The host requests the player to route the
         * document to the explicit external-open fallback instead of failing silently (S0301
         * Phase 04).
         */
        fun onRequireExternalFallback(mediaFile: MediaFile)
    }
}

/**
 * Default no-op host used by every market flavor (S0301 Phase 03).
 *
 * [open] always returns false so the player keeps the S0299 external-handoff behavior;
 * no Office engine is present in market builds.
 */
object NoOpOfficeDocumentViewerHost : OfficeDocumentViewerHost {
    override val isActive: Boolean = false
    override fun isInFullscreenMode(): Boolean = false
    override fun open(mediaFile: MediaFile, file: File): Boolean = false
    override fun release() {}
    override fun print(): Boolean = false
    override fun extractPlainText(): String? = null
}
