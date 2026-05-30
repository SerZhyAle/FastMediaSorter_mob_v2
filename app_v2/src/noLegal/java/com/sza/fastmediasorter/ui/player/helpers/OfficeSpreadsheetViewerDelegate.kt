package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.common.OfficeDocumentFamily
import java.io.File

/**
 * Spreadsheet-family render delegate for the noLegal Office viewer (S0301 Phase 04).
 *
 * Keeps sheet-oriented navigation and read-only workbook state isolated from the Word-family
 * flow so that unsupported spreadsheet features (formulas, charts, macros) degrade cleanly to
 * the explicit external fallback instead of corrupting the page-oriented Word path.
 *
 * The delegate owns no Android view state; it only normalizes a workbook into preview HTML via
 * the shared [OfficeDocumentEngineBridge]. Sheet navigation is expressed as in-page anchors,
 * matching the preview-level quality contract from strategic §6.1.
 */
class OfficeSpreadsheetViewerDelegate(
    private val engineBridge: OfficeDocumentEngineBridge,
) {

    /** Extensions handled by the spreadsheet flow. `.xls` legacy binary still routes to fallback. */
    val supportedExtensions: Set<String> = setOf("xls", "xlsx", "ods")

    /** Family this delegate is responsible for. */
    val family: OfficeDocumentFamily = OfficeDocumentFamily.SPREADSHEET

    /** True when this delegate owns the given local file by extension. */
    fun handles(file: File): Boolean = file.extension.lowercase() in supportedExtensions

    /**
     * Render the workbook to read-only preview HTML, or null when the engine cannot render it
     * (legacy `.xls` binary or a parse failure) so the caller can route to external fallback.
     */
    fun render(file: File): String? = engineBridge.renderToHtml(file, family)
}
