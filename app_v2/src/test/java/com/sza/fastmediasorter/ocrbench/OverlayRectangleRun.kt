package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect
import com.sza.fastmediasorter.domain.ocr.OverlayPlateBounds
import com.sza.fastmediasorter.domain.ocr.OverlayPlateGeometry
import com.sza.fastmediasorter.domain.ocr.OverlaySourceBox
import com.sza.fastmediasorter.domain.ocr.OverlayTranslationExtent

/**
 * S1716: one scene put through the app's own plate geometry, producing rectangles and a duration.
 *
 * This is strategic §5.2's "run" step, narrowed by the owner decision of 2026-08-17 to what can be
 * measured without a raster. It deliberately calls [OverlayPlateGeometry] rather than re-deriving
 * the plate: a bench with its own copy of the arithmetic would score its approximation of the app.
 */
object OverlayRectangleRun {

    /**
     * What the app would lay out for one recognised block: where the source text stands, and how
     * large the translated text measured once wrapped.
     */
    data class TranslatedBlock(
        val sourceBox: Rect,
        val translationWidth: Float,
        val translationHeight: Float,
        val padding: Float,
    )

    /** The plates one run produced, and how long producing them took. */
    data class RectangleRunResult(
        val sceneId: String,
        val plates: List<OverlayPlateBounds>,
        val durationNanos: Long,
    )

    /**
     * A run that could not happen. It is an exception rather than an empty result on purpose: an
     * empty plate list is indistinguishable from a scene where the overlay drew nothing, and §2.4
     * exists precisely because those two must never look alike.
     */
    class RunFailure(message: String) : IllegalStateException(message)

    /**
     * Run [blocks] against [annotation].
     *
     * @param viewBottom the drawing surface's bottom edge, normally the scene's own height.
     * @param nanoClock seam for the duration axis, so a test can assert an exact number instead of
     *   whatever the host happened to measure.
     */
    fun run(
        annotation: SceneAnnotation,
        blocks: List<TranslatedBlock>,
        viewBottom: Float,
        nanoClock: () -> Long = System::nanoTime,
    ): RectangleRunResult {
        requireScorable(annotation)
        if (blocks.isEmpty()) {
            throw RunFailure(
                "scene '${annotation.sceneId}': the recogniser returned no block. " +
                    "A run with nothing to place is unmeasured, not a perfect score."
            )
        }
        val started = nanoClock()
        val plates = blocks.map { block -> plateFor(annotation.sceneId, block, viewBottom) }
        return RectangleRunResult(annotation.sceneId, plates, nanoClock() - started)
    }

    private fun requireScorable(annotation: SceneAnnotation) {
        if (annotation.isScorable()) return
        val why = when {
            annotation.provenance.draft -> "its annotation is a draft filled from a recogniser"
            !annotation.readable -> "the scene is marked unreadable"
            else -> "its annotation carries no text area"
        }
        throw RunFailure("scene '${annotation.sceneId}' cannot be scored: $why.")
    }

    private fun plateFor(sceneId: String, block: TranslatedBlock, viewBottom: Float): OverlayPlateBounds {
        val box = block.sourceBox
        if (box.width() <= 0 || box.height() <= 0) {
            throw RunFailure("scene '$sceneId': a block has an empty source box $box.")
        }
        if (block.translationWidth < 0f || block.translationHeight < 0f || block.padding < 0f) {
            throw RunFailure("scene '$sceneId': a block measured a negative extent.")
        }
        return OverlayPlateGeometry.plateBounds(
            source = OverlaySourceBox(
                left = box.left.toFloat(),
                top = box.top.toFloat(),
                width = box.width().toFloat(),
                height = box.height().toFloat(),
            ),
            translation = OverlayTranslationExtent(
                width = block.translationWidth,
                height = block.translationHeight,
                padding = block.padding,
            ),
            viewBottom = viewBottom,
        )
    }
}
