package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect

/**
 * Models a recognized text fragment with its spatial coordinates and confidence.
 */
data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)
