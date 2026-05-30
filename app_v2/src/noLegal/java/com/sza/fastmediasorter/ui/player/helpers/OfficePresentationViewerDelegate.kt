package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.common.OfficeDocumentFamily
import java.io.File

/**
 * Presentation-family render delegate for the noLegal Office viewer (S0301 Phase 04).
 *
 * Keeps slide-oriented navigation and presentation-only state isolated from the Word-family and
 * spreadsheet flows. Each slide is rendered as a discrete preview section; transitions,
 * animations, and embedded media are intentionally out of the phase-1 preview scope and route
 * to the explicit external fallback when the engine cannot render the file.
 *
 * The delegate owns no Android view state; it only normalizes a deck into preview HTML via the
 * shared [OfficeDocumentEngineBridge].
 */
class OfficePresentationViewerDelegate(
    private val engineBridge: OfficeDocumentEngineBridge,
) {

    /** Extensions handled by the presentation flow. `.ppt` legacy binary still routes to fallback. */
    val supportedExtensions: Set<String> = setOf("ppt", "pptx", "odp")

    /** Family this delegate is responsible for. */
    val family: OfficeDocumentFamily = OfficeDocumentFamily.PRESENTATION

    /** True when this delegate owns the given local file by extension. */
    fun handles(file: File): Boolean = file.extension.lowercase() in supportedExtensions

    /**
     * Render the deck to read-only preview HTML, or null when the engine cannot render it
     * (legacy `.ppt` binary or a parse failure) so the caller can route to external fallback.
     */
    fun render(file: File): String? = engineBridge.renderToHtml(file, family)
}
