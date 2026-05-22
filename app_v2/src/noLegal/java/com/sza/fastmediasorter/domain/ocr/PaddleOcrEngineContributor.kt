package com.sza.fastmediasorter.domain.ocr

import com.sza.fastmediasorter.di.PaddleOfflineOcr
import com.sza.fastmediasorter.domain.model.AppSettings
import javax.inject.Inject

class PaddleOcrEngineContributor @Inject constructor(
    @PaddleOfflineOcr override val engine: OfflineOcrEngine
) : OcrEngineContributor {

    override val engineType: String = "PADDLE_OCR"

    override fun supports(settings: AppSettings, sourceLangCode: String): Boolean {
        return settings.ocrEngineType == engineType && sourceLangCode.lowercase() in CYRILLIC_SOURCE_CODES
    }

    private companion object {
        private val CYRILLIC_SOURCE_CODES = setOf(
            "auto",
            "ru",
            "uk",
            "bg",
            "be",
            "mk",
            "rus",
            "ukr",
            "bul",
            "bel"
        )
    }
}
