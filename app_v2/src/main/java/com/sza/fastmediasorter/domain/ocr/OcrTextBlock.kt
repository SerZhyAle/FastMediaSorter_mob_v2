package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect

/**
 * Models a recognized text fragment with its spatial coordinates and confidence.
 *
 * [words] is null when the engine reports no word level at all, and empty when it reports a word level but
 * found no usable word on this line. Consumers must not collapse the two: null means "keep the previous
 * behaviour", empty means "the engine looked and there was nothing".
 */
data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float,
    val words: List<OcrWord>? = null
)
