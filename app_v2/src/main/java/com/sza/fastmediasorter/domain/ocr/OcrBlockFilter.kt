package com.sza.fastmediasorter.domain.ocr

/**
 * S1712: the single place that decides whether a recognised fragment reaches the overlay, and the
 * single place that can say why one did not.
 *
 * Before this existed the four thresholds lived inside one `filter` lambda and returned a bare
 * boolean, so a discarded fragment left no trace: a page where the engine found nothing and a page
 * where we threw away four correctly read captions looked identical - an empty overlay and "no text
 * found". None of the four numbers could be defended or refuted, because no data about their effect
 * existed (strategic §1).
 *
 * The verdict carries the reason instead of the pipeline and the diagnostics each evaluating the
 * conditions themselves. Two copies of a threshold pass every equality check and diverge the first
 * time one of them is edited, which is why ADR-1 makes this one function with two readers rather
 * than one function per reader.
 */
object OcrBlockFilter {

    /** Minimum engine confidence, in percent, for a fragment to be shown. */
    const val MIN_CONFIDENCE = 30f

    /** Minimum trimmed text length; shorter fragments are noise more often than words. */
    const val MIN_TEXT_LENGTH = 3

    /** Maximum ratio of punctuation-like characters to letters. */
    const val MAX_SPECIAL_TO_LETTER_RATIO = 0.5f

    /** Minimum bounding-box width in pixels. */
    const val MIN_BOX_WIDTH = 20

    /** Minimum bounding-box height in pixels. */
    const val MIN_BOX_HEIGHT = 10

    /**
     * Which condition a fragment failed, or [ACCEPTED] when it passed all four.
     *
     * A named condition rather than a bare "rejected" is strategic §11 criterion 2: the record has to
     * say which threshold to argue with.
     */
    enum class Verdict {
        ACCEPTED,
        LOW_CONFIDENCE,
        TEXT_TOO_SHORT,
        TOO_MANY_SPECIAL_CHARS,
        BOX_TOO_SMALL,
    }

    /**
     * Evaluates [block] against the four thresholds, in the order they were applied before this
     * function existed - the order decides which reason a fragment failing several of them reports.
     */
    fun evaluate(block: OcrTextBlock): Verdict = when {
        block.confidence < MIN_CONFIDENCE -> Verdict.LOW_CONFIDENCE
        block.text.trim().length < MIN_TEXT_LENGTH -> Verdict.TEXT_TOO_SHORT
        specialToLetterRatio(block.text) > MAX_SPECIAL_TO_LETTER_RATIO -> Verdict.TOO_MANY_SPECIAL_CHARS
        block.boundingBox.width() < MIN_BOX_WIDTH -> Verdict.BOX_TOO_SMALL
        block.boundingBox.height() < MIN_BOX_HEIGHT -> Verdict.BOX_TOO_SMALL
        else -> Verdict.ACCEPTED
    }

    /** True when the fragment reaches translation and the overlay. */
    fun isAccepted(block: OcrTextBlock): Boolean = evaluate(block) == Verdict.ACCEPTED

    /**
     * Punctuation per letter. A fragment with no letters at all is infinitely "special" and is meant
     * to fail the ratio test rather than divide by zero.
     */
    private fun specialToLetterRatio(text: String): Float {
        val letters = text.count { it.isLetter() }
        val special = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        return if (letters > 0) special.toFloat() / letters else Float.MAX_VALUE
    }
}
