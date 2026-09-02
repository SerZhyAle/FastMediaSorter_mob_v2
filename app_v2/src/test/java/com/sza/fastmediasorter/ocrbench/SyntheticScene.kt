package com.sza.fastmediasorter.ocrbench

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * S1716: scenes drawn from their own description, together with the exact annotation of what was drawn.
 *
 * Everything here is a constant: no clock, no randomness, no device metrics. Re-drawing a scene has to yield
 * the same bytes, because that is what makes a later difference attributable to the change under test rather
 * than to the scene drifting underneath it.
 *
 * Each scene targets one property the overlay must hold, named in the strategic §5 list.
 */
object SyntheticScene {

    /** One built scene: the pixels and the truth about them. */
    data class Built(
        val bitmap: Bitmap,
        val annotation: SceneAnnotation,
    )

    private const val WIDTH = 800
    private const val HEIGHT = 600
    private const val MARGIN = 40
    private const val LINE_HEIGHT = 48
    private const val BODY_TEXT_SIZE = 32f
    private const val SMALL_CAPS_TEXT_SIZE = 30f
    private const val ARTIFACT_WIDTH = 6
    private const val ARTIFACT_HEIGHT = 140
    private const val PANEL_INSET = 24
    private const val AUTHOR = "S1716 synthetic builder"
    private const val ANNOTATED_ON = "2026-08-16"

    /** Every scene, in a fixed order, so a report's rows are comparable between runs. */
    fun all(): List<Built> = listOf(
        lineWithTallArtifact(),
        lineInSmallCapitals(),
        darkPanel(),
        longTranslationInTightBox(),
        uniformMultilineText(),
        lineWithVariedWordHeights(),
    )

    /** A line whose box is stretched by one tall stroke - the defect S1711 fixed, kept as a guard. */
    fun lineWithTallArtifact(): Built {
        val bitmap = blankScene(Color.WHITE)
        val canvas = Canvas(bitmap)
        val textBox = Rect(MARGIN, MARGIN, MARGIN + 360, MARGIN + LINE_HEIGHT)
        drawTextIn(canvas, "Hello world", textBox, BODY_TEXT_SIZE, Color.BLACK)
        val artifactLeft = MARGIN + 500
        canvas.drawRect(
            artifactLeft.toFloat(),
            MARGIN.toFloat(),
            (artifactLeft + ARTIFACT_WIDTH).toFloat(),
            (MARGIN + ARTIFACT_HEIGHT).toFloat(),
            fill(Color.BLACK)
        )
        return Built(
            bitmap,
            annotation(
                id = "line-with-tall-artifact",
                textAreas = listOf(TextArea("Hello world", textBox)),
                paintable = listOf(PaintableArea(Rect(MARGIN, MARGIN, MARGIN + 460, MARGIN + LINE_HEIGHT))),
            )
        )
    }

    /** Small capitals: every glyph is tall, so a height rule alone would drop a real word. */
    fun lineInSmallCapitals(): Built {
        val bitmap = blankScene(Color.WHITE)
        val canvas = Canvas(bitmap)
        val textBox = Rect(MARGIN, MARGIN, MARGIN + 420, MARGIN + LINE_HEIGHT)
        drawTextIn(canvas, "ROME AND CARTHAGE", textBox, SMALL_CAPS_TEXT_SIZE, Color.BLACK)
        return Built(
            bitmap,
            annotation(
                id = "line-in-small-capitals",
                textAreas = listOf(TextArea("ROME AND CARTHAGE", textBox)),
                paintable = listOf(PaintableArea(textBox)),
            )
        )
    }

    /** Light text on a dark panel: the plate's colour decision has to read the panel, not the page. */
    fun darkPanel(): Built {
        val bitmap = blankScene(Color.WHITE)
        val canvas = Canvas(bitmap)
        val panel = Rect(MARGIN, MARGIN, WIDTH - MARGIN, MARGIN + LINE_HEIGHT * 2)
        canvas.drawRect(panel, fill(Color.DKGRAY))
        val textBox = Rect(
            panel.left + PANEL_INSET,
            panel.top + PANEL_INSET,
            panel.left + PANEL_INSET + 300,
            panel.top + PANEL_INSET + LINE_HEIGHT
        )
        drawTextIn(canvas, "Night mode", textBox, BODY_TEXT_SIZE, Color.WHITE)
        return Built(
            bitmap,
            annotation(
                id = "dark-panel",
                textAreas = listOf(TextArea("Night mode", textBox)),
                paintable = listOf(PaintableArea(panel)),
            )
        )
    }

    /** A short source line whose translation is much longer - the plate must not grow past its paper. */
    fun longTranslationInTightBox(): Built {
        val bitmap = blankScene(Color.WHITE)
        val canvas = Canvas(bitmap)
        val textBox = Rect(MARGIN, MARGIN, MARGIN + 160, MARGIN + LINE_HEIGHT)
        drawTextIn(canvas, "Exit", textBox, BODY_TEXT_SIZE, Color.BLACK)
        // The drawing to the right is what the plate must not cover.
        val drawing = Rect(MARGIN + 200, MARGIN, MARGIN + 420, MARGIN + LINE_HEIGHT * 3)
        canvas.drawRect(drawing, fill(Color.RED))
        return Built(
            bitmap,
            annotation(
                id = "long-translation-in-tight-box",
                textAreas = listOf(TextArea("Exit", textBox)),
                paintable = listOf(PaintableArea(Rect(MARGIN, MARGIN, MARGIN + 180, MARGIN + LINE_HEIGHT))),
            )
        )
    }

    /** Four identical lines: nothing here should be dropped, and the plates must not merge. */
    fun uniformMultilineText(): Built {
        val bitmap = blankScene(Color.WHITE)
        val canvas = Canvas(bitmap)
        val texts = mutableListOf<TextArea>()
        for (index in 0 until UNIFORM_LINE_COUNT) {
            val top = MARGIN + index * (LINE_HEIGHT + UNIFORM_LINE_GAP)
            val box = Rect(MARGIN, top, MARGIN + 380, top + LINE_HEIGHT)
            drawTextIn(canvas, "The same line", box, BODY_TEXT_SIZE, Color.BLACK)
            texts.add(TextArea("The same line", box))
        }
        return Built(
            bitmap,
            annotation(
                id = "uniform-multiline-text",
                textAreas = texts,
                paintable = texts.map { PaintableArea(it.box) },
            )
        )
    }

    /**
     * S2036: one line whose four words genuinely differ in height, so the height-relation axes have a
     * response on reproducible material.
     *
     * The four heights are authored, which is exactly why this scene can never answer the question the
     * axes exist for - it measures the author, not our material. It is here to prove the arithmetic
     * runs and to keep the axes from having "unmeasured" as their only observed value.
     */
    fun lineWithVariedWordHeights(): Built {
        val bitmap = blankScene(Color.WHITE)
        val canvas = Canvas(bitmap)
        val lineBox = Rect(MARGIN, MARGIN, MARGIN + VARIED_LINE_WIDTH, MARGIN + LINE_HEIGHT)
        val words = VARIED_WORDS.map { word ->
            val box = Rect(
                MARGIN + word.leftOffset,
                MARGIN + word.topInset,
                MARGIN + word.leftOffset + word.width,
                MARGIN + word.topInset + word.height,
            )
            drawTextIn(canvas, word.text, box, BODY_TEXT_SIZE, Color.BLACK)
            AnnotatedWord(word.text, box)
        }
        return Built(
            bitmap,
            annotation(
                id = "line-with-varied-word-heights",
                textAreas = listOf(TextArea(VARIED_WORDS.joinToString(" ") { it.text }, lineBox, words)),
                paintable = listOf(PaintableArea(lineBox)),
            )
        )
    }

    /** Authored geometry of one word of [lineWithVariedWordHeights], relative to the scene margin. */
    private data class VariedWord(
        val text: String,
        val leftOffset: Int,
        val width: Int,
        val topInset: Int,
        val height: Int,
    )

    private const val VARIED_LINE_WIDTH = 360

    /**
     * Heights 30, 26, 18 and 40 - median 28, maximum 40. Median and maximum differ on purpose: an axis
     * that aggregated one where it meant the other would still pass on a scene where they coincide.
     */
    private val VARIED_WORDS = listOf(
        VariedWord(text = "Big", leftOffset = 0, width = 90, topInset = 8, height = 30),
        VariedWord(text = "gyp", leftOffset = 100, width = 90, topInset = 16, height = 26),
        VariedWord(text = "xx", leftOffset = 200, width = 60, topInset = 16, height = 18),
        VariedWord(text = "Ej", leftOffset = 270, width = 70, topInset = 4, height = 40),
    )

    private const val UNIFORM_LINE_COUNT = 4
    private const val UNIFORM_LINE_GAP = 16

    private fun blankScene(background: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(background)
        return bitmap
    }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun drawTextIn(canvas: Canvas, text: String, box: Rect, sizePx: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizePx
        }
        // Baseline from the box rather than from font metrics: metrics differ between JVM and device, and a
        // scene that moves by a pixel between hosts is not a fixed scene.
        val baseline = box.bottom - (box.height() / 4f)
        canvas.drawText(text, box.left.toFloat(), baseline, paint)
    }

    private fun annotation(
        id: String,
        textAreas: List<TextArea>,
        paintable: List<PaintableArea>,
    ): SceneAnnotation = SceneAnnotation(
        version = SceneAnnotation.CURRENT_VERSION,
        sceneId = id,
        widthPx = WIDTH,
        heightPx = HEIGHT,
        textAreas = textAreas,
        paintableAreas = paintable,
        readable = true,
        provenance = Provenance(author = AUTHOR, annotatedOn = ANNOTATED_ON, draft = false),
    )
}
