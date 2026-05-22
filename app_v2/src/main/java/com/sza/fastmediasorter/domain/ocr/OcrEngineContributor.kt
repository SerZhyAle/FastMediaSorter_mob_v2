package com.sza.fastmediasorter.domain.ocr

import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Flavor extension point for OCR engines that should remain outside src/main.
 */
interface OcrEngineContributor {
    val engineType: String
    val engine: OfflineOcrEngine

    fun supports(settings: AppSettings, sourceLangCode: String): Boolean
}
