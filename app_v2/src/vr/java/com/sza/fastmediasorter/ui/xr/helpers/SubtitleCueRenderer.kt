package com.sza.fastmediasorter.ui.xr.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface

/**
 * S0986: Context-free Canvas painter for the immersive subtitle-cue quad.
 *
 * Mirrors [HudCanvasRenderer]'s Context-free contract - the Activity feeds already-extracted cue
 * text and the renderer paints a readable, centered caption (semi-opaque box + white bold text)
 * into a fixed-size bitmap that is uploaded to the subtitle quad via the same direct-buffer path as
 * the HUD. Line wrapping is char-budget based (not `measureText`) so [buildLines] stays pure and
 * JVM-unit-testable - the one piece of this VR feature verifiable without a headset.
 */
class SubtitleCueRenderer {

    companion object {
        const val WIDTH = 1024
        const val HEIGHT = 256

        // Owner decision (S0986): 2 lines per cue, overflow clipped (no scroll, no font shrink).
        const val MAX_LINES = 2

        // Char budget per line at TEXT_SIZE on a WIDTH-wide texture (bold ~64px glyphs on 1024 px).
        const val MAX_CHARS_PER_LINE = 32

        private const val HALF = 0.5f
        private const val TEXT_SIZE = 64f
        private const val LINE_SPACING = 84f
        private const val BOX_MARGIN = 24f
        private const val BOX_RADIUS = 24f
        private const val BG_ALPHA = 160
        private const val SHADOW_RADIUS = 6f
        private const val SHADOW_DY = 2f
    }

    private val bgPaint = Paint().apply {
        color = Color.argb(BG_ALPHA, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = TEXT_SIZE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        // Drop shadow keeps white text readable over bright video frames behind the quad.
        setShadowLayer(SHADOW_RADIUS, 0f, SHADOW_DY, Color.BLACK)
    }

    /** Paint [text] as up to [MAX_LINES] centered lines. Caller gates empty text (hides the quad). */
    fun render(canvas: Canvas, text: String) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val lines = buildLines(text, MAX_CHARS_PER_LINE)
        if (lines.isEmpty()) return

        val blockH = LINE_SPACING * lines.size
        val boxTop = (HEIGHT - blockH) * HALF - BOX_MARGIN
        val boxBottom = (HEIGHT + blockH) * HALF + BOX_MARGIN
        canvas.drawRoundRect(
            RectF(BOX_MARGIN, boxTop, WIDTH - BOX_MARGIN, boxBottom),
            BOX_RADIUS, BOX_RADIUS, bgPaint
        )

        var baseline = (HEIGHT - blockH) * HALF + TEXT_SIZE
        for (line in lines) {
            canvas.drawText(line, WIDTH * HALF, baseline, textPaint)
            baseline += LINE_SPACING
        }
    }
}

/**
 * S0986: split a cue string into at most [SubtitleCueRenderer.MAX_LINES] display lines - honoring
 * explicit newlines first, then greedy word-wrap, clipping any overflow onto the last line with
 * "..". Pure (no Android deps) so it is JVM unit-testable.
 */
internal fun buildLines(raw: String, maxChars: Int): List<String> {
    val normalized = raw.trim()
    if (normalized.isEmpty()) return emptyList()

    val explicit = normalized.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    val wrapped: List<String> = if (explicit.size >= SubtitleCueRenderer.MAX_LINES) {
        explicit
    } else {
        wrapWords(explicit.joinToString(" "), maxChars)
    }

    if (wrapped.size <= SubtitleCueRenderer.MAX_LINES) {
        return wrapped.map { clip(it, maxChars) }
    }
    // More lines than allowed: keep the first N-1, fold the rest into the last line, ellipsize it.
    val head = wrapped.take(SubtitleCueRenderer.MAX_LINES - 1).map { clip(it, maxChars) }
    val tail = wrapped.drop(SubtitleCueRenderer.MAX_LINES - 1).joinToString(" ")
    return head + ellipsize(tail, maxChars)
}

private fun wrapWords(text: String, maxChars: Int): List<String> {
    val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val lines = mutableListOf<String>()
    val current = StringBuilder()
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (candidate.length <= maxChars) {
            current.clear()
            current.append(candidate)
            continue
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
            current.clear()
        }
        if (word.length > maxChars) {
            var rest = word
            while (rest.length > maxChars) {
                lines.add(rest.take(maxChars))
                rest = rest.drop(maxChars)
            }
            current.append(rest)
        } else {
            current.append(word)
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
}

private fun clip(line: String, maxChars: Int): String =
    if (line.length <= maxChars) line else ellipsize(line, maxChars)

private fun ellipsize(line: String, maxChars: Int): String {
    if (line.length <= maxChars) return line
    val keep = (maxChars - 2).coerceAtLeast(0)
    return line.take(keep) + ".."
}
