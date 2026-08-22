package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect

/**
 * S1711: the two geometric values of a recognised line, derived from its words instead of from its box.
 *
 * A line box is the union of its word boxes, so one tall artifact - a speech-bubble outline read as `|`, a
 * plate border, a stray stroke - sets the type size of the whole line and stretches its plate. Both values
 * live here rather than in the drawing layer because they are the only part of the overlay that can be
 * measured without a device.
 */
object OcrLineGeometry {

    /**
     * How many times taller than the line's median a word must be before it can be called an artifact.
     *
     * Inherited from the neighbouring project's measurement, not derived on our own material. S1717 owns
     * deriving it; until then, changing it here is a guess with a number attached.
     */
    const val DEFAULT_MAX_HEIGHT_RATIO: Float = 2.0f

    /**
     * Type size of [block] in pixels: the median height of its words.
     *
     * For an even number of words the lower of the two middle heights is returned, so a two-word line yields
     * a height one of its words actually has rather than an average belonging to neither.
     *
     * Falls back to the box height when the engine reported no words, which is the behaviour that shipped
     * before this rule existed.
     */
    fun typeSizePx(block: OcrTextBlock): Int {
        val heights = block.words?.map { it.boundingBox.height() }?.sorted()
        if (heights.isNullOrEmpty()) {
            return block.boundingBox.height()
        }
        return heights[(heights.size - 1) / 2]
    }

    /**
     * True when [word] is an artifact rather than text: it carries no letter and no digit, **and** it is
     * taller than [medianHeightPx] times [maxHeightRatio].
     *
     * Both conditions are required. "No letters" alone deletes a comma or a full stop; "tall" alone deletes
     * a real word in a line set in small capitals.
     */
    fun isArtifactWord(word: OcrWord, medianHeightPx: Int, maxHeightRatio: Float): Boolean {
        val hasTextCharacter = word.text.any { Character.isLetterOrDigit(it) }
        return !hasTextCharacter && word.boundingBox.height() > medianHeightPx * maxHeightRatio
    }

    /**
     * Box of [block] rebuilt from the words that survive [isArtifactWord].
     *
     * Returns the original box unchanged when the engine reported no words, and when every word was dropped:
     * a line left with nothing has nothing to shrink to.
     */
    fun tightenedBounds(block: OcrTextBlock, maxHeightRatio: Float = DEFAULT_MAX_HEIGHT_RATIO): Rect {
        val median = typeSizePx(block)
        val survivors = block.words.orEmpty().filterNot { isArtifactWord(it, median, maxHeightRatio) }
        if (survivors.isEmpty()) {
            return block.boundingBox
        }
        return Rect(
            survivors.minOf { it.boundingBox.left },
            survivors.minOf { it.boundingBox.top },
            survivors.maxOf { it.boundingBox.right },
            survivors.maxOf { it.boundingBox.bottom }
        )
    }
}
