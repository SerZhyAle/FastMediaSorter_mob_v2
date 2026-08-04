package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.RectF
import android.graphics.pdf.content.PdfPageTextContent
import androidx.annotation.RequiresApi

/**
 * S1276: where each word of a PDF page's native text sits on the rendered bitmap.
 *
 * Built from `PdfRenderer.Page.getTextContents()` (API 35+), which returns the page text **and** its
 * geometry in one call. That is the whole point of this class: before it, locating the word under a
 * long-press meant a second, full-page OCR pass whose only output was a bounding box - on a device
 * that had just read the same geometry from the platform for free.
 *
 * Ranges are exact by construction: the text and the per-item ranges are produced by one pass, so
 * nothing has to be searched for afterwards. Do not "unify" this with
 * [PdfSelectionCoordinateMapper.charRangeForPoint] - that one resolves a word by
 * `fullText.indexOf(word)` and therefore always selects the *first* occurrence on the page. It has
 * to, because OCR hands it a word string and no offset; this class has the offset.
 *
 * **Granularity is per word, and it has to be.** A content item is a run of text, not a word: the
 * 2026-07-29 device run found `getTextContents()` returning a *single* item spanning an entire
 * 1884-character page, so selecting whole items reproduced the complaint this ticket exists to fix -
 * three presses on well-separated words gave a byte-identical whole-page selection. Each item does
 * carry one box per line it spans, so [Companion.wordItems] cuts the run into lines against those
 * boxes and then into words inside each line. Word x-spans are interpolated from character counts,
 * which is approximate for a proportional font; the lookup therefore falls back to the nearest word
 * centre rather than demanding strict containment, and the user still adjusts with the native
 * handles.
 */
class PdfNativeTextLayout(
    val text: String,
    val items: List<Item>,
) {

    /** One item: the characters it owns in [text], and its boxes in bitmap pixels. */
    data class Item(val range: IntRange, val boxes: List<RectF>)

    /**
     * Character range of the item under ([x], [y]) in bitmap pixel coordinates, or of the nearest
     * item when the point falls between them. Null only when there is no text.
     */
    fun charRangeForPoint(x: Float, y: Float): IntRange? {
        val containing = items.firstOrNull { item -> item.boxes.any { it.contains(x, y) } }
        val target = containing ?: items.minByOrNull { item ->
            item.boxes.minOfOrNull { box -> squaredDistanceToCenter(box, x, y) } ?: Float.MAX_VALUE
        }
        return target?.range
    }

    private fun squaredDistanceToCenter(box: RectF, x: Float, y: Float): Float {
        val dx = box.centerX() - x
        val dy = box.centerY() - y
        return dx * dx + dy * dy
    }

    companion object {
        /**
         * `PdfRenderer.Page.getTextContents()` and the bounds on each item landed in API 35. One
         * home for the number - [PdfTextSelectionManager] branches on the same constant.
         */
        const val API_NATIVE_PDF_TEXT = 35

        /** Must match the separator the page text is assembled with, or every range drifts. */
        private const val SEPARATOR = " "

        /**
         * How far a proportional line cut may travel to land on whitespace instead of mid-word.
         * Beyond this the cut stays where the arithmetic put it - a run with no spaces (CJK, a long
         * token) must not make the search scan the whole page.
         */
        private const val MAX_CUT_SNAP = 16

        /**
         * Assemble the page text and the per-word boxes in one pass.
         *
         * Platform bounds are in PDF page points; the bitmap is a straight render of the same page,
         * so a per-axis scale converts them. A non-positive page dimension yields an empty layout
         * rather than a division - callers fall back to the OCR path on an empty one.
         */
        @RequiresApi(API_NATIVE_PDF_TEXT)
        fun from(
            contents: List<PdfPageTextContent>,
            pageWidth: Int,
            pageHeight: Int,
            bitmapWidth: Int,
            bitmapHeight: Int,
        ): PdfNativeTextLayout {
            if (pageWidth <= 0 || pageHeight <= 0) return PdfNativeTextLayout("", emptyList())
            val scaleX = bitmapWidth / pageWidth.toFloat()
            val scaleY = bitmapHeight / pageHeight.toFloat()
            val builder = StringBuilder()
            val items = mutableListOf<Item>()
            for (content in contents) {
                val piece = content.text
                // A blank item would otherwise become a nearest-match candidate with nothing to select.
                if (piece.isBlank()) continue
                if (builder.isNotEmpty()) builder.append(SEPARATOR)
                val start = builder.length
                builder.append(piece)
                items += wordItems(piece, content.bounds.map { scaled(it, scaleX, scaleY) }, start)
            }
            return PdfNativeTextLayout(builder.toString(), items)
        }

        /**
         * Cut one content run into per-word items. [offset] is where [text] starts in the page text,
         * [boxes] are the run's line boxes already in bitmap pixels.
         *
         * Platform-free on purpose: the factory above needs API 35 types, this does not, so the split
         * that decides what a press selects stays unit-testable.
         */
        internal fun wordItems(text: String, boxes: List<RectF>, offset: Int): List<Item> {
            if (boxes.isEmpty() || text.isEmpty()) return emptyList()
            val items = mutableListOf<Item>()
            // One line per box, in order - splitIntoLines keeps that 1:1 even when a line comes out
            // empty, because pairing a line with the wrong box would move every word on the page.
            for ((index, line) in splitIntoLines(text, boxes).withIndex()) {
                val box = boxes.getOrNull(index) ?: break
                items += wordsInLine(line, box, offset)
            }
            // A run of pure punctuation or an unsplittable line still has to be selectable.
            return items.ifEmpty { listOf(Item(offset until offset + text.length, boxes)) }
        }

        /** One line of a content run: its text and where that text starts inside the run. */
        private data class Line(val start: Int, val text: String)

        /**
         * Split [text] into one line per box. Real newlines win when the run carries exactly as many
         * as it has boxes - that is the run telling us where its lines end. Otherwise the characters
         * are apportioned by box width, since a wider line box holds proportionally more of the same
         * run; that is the case the device run hit, where a whole page arrived as one item.
         */
        private fun splitIntoLines(text: String, boxes: List<RectF>): List<Line> {
            if (boxes.size <= 1) return listOf(Line(0, text))
            val byNewline = splitOnNewlines(text)
            return if (byNewline.size == boxes.size) byNewline else splitByBoxWidth(text, boxes)
        }

        /**
         * Apportion [text] across [boxes] by width. Every box gets a line, even an empty one, so the
         * caller can pair line N with box N. Each cut travels up to [MAX_CUT_SNAP] characters to land
         * on whitespace so a word is not split across two lines; a run without spaces keeps the
         * arithmetic cut.
         */
        private fun splitByBoxWidth(text: String, boxes: List<RectF>): List<Line> {
            val total = boxes.sumOf { it.width().toDouble() }
            if (total <= 0.0) return listOf(Line(0, text))
            val lines = mutableListOf<Line>()
            var start = 0
            var consumed = 0.0
            for ((index, box) in boxes.withIndex()) {
                consumed += box.width().toDouble()
                val end = if (index == boxes.lastIndex) {
                    text.length
                } else {
                    snapToWhitespace(text, (text.length * consumed / total).toInt().coerceIn(start, text.length))
                }
                lines.add(Line(start, text.substring(start, end.coerceAtLeast(start))))
                start = end.coerceAtLeast(start)
            }
            return lines
        }

        /** Nearest whitespace to [at], searching both ways, or [at] itself when there is none nearby. */
        private fun snapToWhitespace(text: String, at: Int): Int {
            if (at >= text.length || text[at].isWhitespace()) return at
            val snapped = (1..MAX_CUT_SNAP)
                .asSequence()
                .map { delta -> whitespaceAtDistance(text, at, delta) }
                .firstOrNull { it >= 0 }
            return snapped ?: at
        }

        /** Whitespace exactly [delta] characters before or after [at], preferring before; -1 if neither. */
        private fun whitespaceAtDistance(text: String, at: Int, delta: Int): Int {
            val back = at - delta
            if (back > 0 && text[back].isWhitespace()) return back
            val forward = at + delta
            return if (forward < text.length && text[forward].isWhitespace()) forward else -1
        }

        private fun splitOnNewlines(text: String): List<Line> {
            val lines = mutableListOf<Line>()
            var start = 0
            while (start <= text.length) {
                val breakAt = text.indexOf('\n', start)
                if (breakAt < 0) {
                    lines.add(Line(start, text.substring(start)))
                    break
                }
                lines.add(Line(start, text.substring(start, breakAt)))
                start = breakAt + 1
            }
            return lines
        }

        /**
         * Words of one line, each with the slice of [box] its characters occupy. The x-span is
         * interpolated from character positions - exact per-glyph advances are not in the API, and the
         * nearest-centre lookup absorbs the error.
         */
        private fun wordsInLine(line: Line, box: RectF, offset: Int): List<Item> {
            val length = line.text.length
            if (length == 0) return emptyList()
            val perChar = box.width() / length
            val items = mutableListOf<Item>()
            var index = 0
            while (index < length) {
                if (line.text[index].isWhitespace()) {
                    index++
                    continue
                }
                var end = index
                while (end < length && !line.text[end].isWhitespace()) end++
                val start = offset + line.start + index
                val wordBox = RectF(
                    box.left + perChar * index,
                    box.top,
                    box.left + perChar * end,
                    box.bottom,
                )
                items.add(Item(start until offset + line.start + end, listOf(wordBox)))
                index = end
            }
            return items
        }

        private fun scaled(box: RectF, scaleX: Float, scaleY: Float) =
            RectF(box.left * scaleX, box.top * scaleY, box.right * scaleX, box.bottom * scaleY)
    }
}
