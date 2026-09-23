package com.sza.fastmediasorter.domain.ocr

/**
 * `OCR-OVERLAY` rule 10, "correct plates, or none": when the recognition language was only assumed, and the
 * translatability filter then refused nearly everything the engine read, the overlay is not drawn.
 *
 * Tesseract never fails on a script mismatch - pointed at Cyrillic or CJK with English data it answers in
 * Latin letters, so the failure is not an empty result but a result [OcrBlockFilter] refuses almost all of.
 * The decision is therefore a reading of the filter's own verdicts, never a copy of its conditions, and needs
 * no second engine pass. A language the user chose explicitly is never second-guessed: there is no evidence
 * for that case, and refusing a stated choice is a different rule.
 *
 * Constant status (`OCR-OVERLAY` rule 13):
 * - [MIN_REFUSED_CHARS] - CHOSEN HERE, NOT DERIVED: keeps a nearly blank picture from being called a
 *   language failure. Mirrors FastMediaSorter_Lite's `OcrLanguageGuard`; no corpus behind it.
 * - [MIN_REFUSED_FRACTION] - CHOSEN HERE, NOT DERIVED: the share of what was read that must have been
 *   refused. Same source.
 *
 * The bias is deliberate: refusing wrongly costs an overlay over a picture that stays as readable as it was;
 * proceeding wrongly covers the picture in transliterated debris.
 */
object OcrLanguageGuard {

    /** CHOSEN HERE, NOT DERIVED - fewest refused letters and digits before a run can be a language failure. */
    const val MIN_REFUSED_CHARS = 40

    /** CHOSEN HERE, NOT DERIVED - share of all letters and digits read that must have been refused. */
    const val MIN_REFUSED_FRACTION = 0.75

    /**
     * True when the run must produce no overlay.
     *
     * @param languageWasAssumed the recognition language was not chosen by the user, or had no engine data
     *   and fell back to another one.
     * @param keptTexts fragments the filter accepted - what would be painted.
     * @param refusedTexts fragments the filter rejected on this run.
     */
    fun shouldRefuse(languageWasAssumed: Boolean, keptTexts: List<String>, refusedTexts: List<String>): Boolean {
        if (!languageWasAssumed) {
            return false
        }
        val refused = refusedTexts.sumOf(::countLettersAndDigits)
        val total = refused + keptTexts.sumOf(::countLettersAndDigits)
        return refused >= MIN_REFUSED_CHARS && refused.toDouble() / total >= MIN_REFUSED_FRACTION
    }

    private fun countLettersAndDigits(text: String): Int =
        text.codePoints().filter { Character.isLetterOrDigit(it) }.count().toInt()
}
