package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect

/**
 * Cuts a recogniser line wherever two consecutive words stand further apart than [MAX_WORD_GAP_RATIO] median
 * word heights, so text from two separated regions does not become one plate painted across the artwork and one
 * sentence handed to the translator.
 *
 * Tesseract assembles such lines itself, and here one recogniser line becomes exactly one plate, so there is no
 * later stage that could still take the stitch apart. The cut runs before [OcrBlockFilter], which then judges
 * every piece on its own: a junk glyph cut away from a real line fails the filter instead of riding along.
 */
object OcrLineSplitter {

    /**
     * Bracketed on our own material, `ocr-overlay-accuracy.md` section 16: at full resolution the widest
     * gap inside an honest line was 3.33 and the narrowest stitch across two regions 3.67. Side-by-side speech
     * bubbles stitch as low as 2.33 and stay unseparable by geometry at any value.
     */
    const val MAX_WORD_GAP_RATIO: Float = 3.5f

    /** Every block of [blocks] replaced by its pieces, in reading order. */
    fun split(blocks: List<OcrTextBlock>, maxGapRatio: Float = MAX_WORD_GAP_RATIO): List<OcrTextBlock> =
        blocks.flatMap { splitLine(it, maxGapRatio) }

    /**
     * Pieces of [block], each boxed to its own words with its own mean confidence; the block itself, by identity,
     * when nothing is cut or when the engine reported no words.
     */
    fun splitLine(block: OcrTextBlock, maxGapRatio: Float = MAX_WORD_GAP_RATIO): List<OcrTextBlock> {
        val runs = runsOf(block, maxGapRatio)
        return if (runs.size < 2) listOf(block) else runs.map(::pieceOf)
    }

    private fun runsOf(block: OcrTextBlock, maxGapRatio: Float): List<List<OcrWord>> {
        val words = block.words.orEmpty()
        val limit = OcrLineGeometry.typeSizePx(block) * maxGapRatio
        if (limit <= 0f) {
            return emptyList()
        }
        val runs = mutableListOf<MutableList<OcrWord>>()
        words.forEachIndexed { index, word ->
            if (index == 0 || OcrLineGap.gapPx(words[index - 1].boundingBox, word.boundingBox) > limit) {
                runs.add(mutableListOf())
            }
            runs.last().add(word)
        }
        return runs
    }

    private fun pieceOf(words: List<OcrWord>): OcrTextBlock =
        OcrTextBlock(
            text = words.joinToString(" ") { it.text },
            boundingBox = Rect(
                words.minOf { it.boundingBox.left },
                words.minOf { it.boundingBox.top },
                words.maxOf { it.boundingBox.right },
                words.maxOf { it.boundingBox.bottom }
            ),
            confidence = words.map { it.confidence }.average().toFloat(),
            words = words
        )
}
