package com.sza.fastmediasorter.domain.ocr

/**
 * S1712 / S1717: the single place that decides whether a recognised fragment reaches the overlay, and the
 * single place that can say why one did not.
 *
 * Before S1712 existed the four thresholds lived inside one `filter` lambda and returned a bare
 * boolean, so a discarded fragment left no trace: a page where the engine found nothing and a page
 * where we threw away four correctly read captions looked identical - an empty overlay and "no text
 * found". None of the four numbers could be defended or refuted, because no data about their effect
 * existed (strategic §1).
 *
 * S1717 reformulates translatability by text properties: CJK ideographs/Kana/Hangul bypass the vowel rule
 * and short-length requirement (allowing 1-2 char signs like "出口"), while abjad scripts (Arabic, Hebrew)
 * bypass the vowel requirement. Alphabetic scripts (Latin, Cyrillic, Greek) require at least one vowel
 * when fragment length >= 3.
 *
 * Threshold derivation status (S1717):
 * - [INHERITED] MIN_CONFIDENCE = 30f (pending S1716 accuracy corpus measurement).
 * - [INHERITED] MIN_TEXT_LENGTH = 3 (for non-CJK text; CJK text allows min 1 char).
 * - [INHERITED] MAX_SPECIAL_TO_LETTER_RATIO = 0.5f (used as ratio bound in translatability check).
 * - [INHERITED] MIN_BOX_WIDTH = 20, MIN_BOX_HEIGHT = 10 (pending S1716 line-relative height derivation, ADR-3).
 */
object OcrBlockFilter {

    /** [INHERITED] Minimum engine confidence, in percent, for a fragment to be shown. */
    const val MIN_CONFIDENCE = 30f

    /** [INHERITED] Minimum trimmed text length for alphabetic text; CJK text allows single/double chars. */
    const val MIN_TEXT_LENGTH = 3

    /** [INHERITED] Maximum ratio of punctuation-like characters to letters/ideographs. */
    const val MAX_SPECIAL_TO_LETTER_RATIO = 0.5f

    /** [INHERITED] Minimum bounding-box width in absolute pixels. */
    const val MIN_BOX_WIDTH = 20

    /** [INHERITED] Minimum bounding-box height in absolute pixels. */
    const val MIN_BOX_HEIGHT = 10

    private val VOWELS = setOf(
        'a', 'e', 'i', 'o', 'u', 'y',
        'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я',
        'α', 'ε', 'η', 'ι', 'ο', 'υ', 'ω'
    )

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
    fun evaluate(block: OcrTextBlock): Verdict {
        val verdict = when {
            block.confidence < MIN_CONFIDENCE -> Verdict.LOW_CONFIDENCE
            isTextTooShort(block.text) -> Verdict.TEXT_TOO_SHORT
            !isTranslatableText(block.text) -> Verdict.TOO_MANY_SPECIAL_CHARS
            block.boundingBox.width() < MIN_BOX_WIDTH -> Verdict.BOX_TOO_SMALL
            block.boundingBox.height() < MIN_BOX_HEIGHT -> Verdict.BOX_TOO_SMALL
            else -> Verdict.ACCEPTED
        }
        return verdict
    }

    /** True when the fragment reaches translation and the overlay. */
    fun isAccepted(block: OcrTextBlock): Boolean = evaluate(block) == Verdict.ACCEPTED

    /**
     * Short text check: CJK/ideographic text allows single and double character fragments (e.g. "出口"),
     * while alphabetic text requires at least [MIN_TEXT_LENGTH] (3) trimmed characters.
     */
    private fun isTextTooShort(text: String): Boolean {
        val trimmed = text.trim()
        val hasCjk = containsCjkOrIdeographic(trimmed)
        return when {
            trimmed.isEmpty() -> true
            hasCjk -> false
            else -> trimmed.length < MIN_TEXT_LENGTH
        }
    }

    /**
     * Reformulated translatability check (S1717):
     * 1. Checks text contains letters/ideographs and special ratio <= [MAX_SPECIAL_TO_LETTER_RATIO].
     * 2. Bypasses vowel rule for CJK/ideographic scripts and abjad scripts (Arabic, Hebrew).
     * 3. Enforces vowel requirement for alphabetic scripts (Latin, Cyrillic, Greek).
     */
    private fun isTranslatableText(text: String): Boolean {
        var letterOrIdeographCount = 0
        var specialCount = 0
        var cjkCount = 0
        var abjadCount = 0
        var vowelCount = 0

        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)

            if (isCjkOrIdeographic(cp)) {
                cjkCount++
                letterOrIdeographCount++
            } else if (Character.isLetter(cp)) {
                letterOrIdeographCount++
                if (isAbjadScript(cp)) {
                    abjadCount++
                } else if (text[i].lowercaseChar() in VOWELS) {
                    vowelCount++
                }
            } else if (!Character.isDigit(cp) && !Character.isWhitespace(cp)) {
                specialCount++
            }
            i += charCount
        }

        val ratio = if (letterOrIdeographCount > 0) specialCount.toFloat() / letterOrIdeographCount else Float.MAX_VALUE
        val validRatio = letterOrIdeographCount > 0 && ratio <= MAX_SPECIAL_TO_LETTER_RATIO
        val validVowelRule = cjkCount > 0 || abjadCount > 0 || vowelCount > 0

        return validRatio && validVowelRule
    }

    private fun containsCjkOrIdeographic(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isCjkOrIdeographic(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun isCjkOrIdeographic(codePoint: Int): Boolean {
        if (Character.isIdeographic(codePoint)) return true
        val block = Character.UnicodeBlock.of(codePoint)
        return block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HANGUL_JAMO ||
            block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
    }

    private fun isAbjadScript(codePoint: Int): Boolean {
        val block = Character.UnicodeBlock.of(codePoint)
        return block == Character.UnicodeBlock.ARABIC ||
            block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
            block == Character.UnicodeBlock.HEBREW
    }
}
