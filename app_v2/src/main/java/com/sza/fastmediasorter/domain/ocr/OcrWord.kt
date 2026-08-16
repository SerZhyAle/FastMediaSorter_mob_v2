package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect

/**
 * One word the recogniser reported inside a single text line, with its own box.
 *
 * [confidence] is carried because the same word-level pass makes it available at no extra cost, and the
 * tickets that read it (S1712, S1717) would otherwise force a second model change.
 */
data class OcrWord(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)
