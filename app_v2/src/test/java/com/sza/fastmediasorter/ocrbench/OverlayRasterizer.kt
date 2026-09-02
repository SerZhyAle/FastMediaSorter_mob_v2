package com.sza.fastmediasorter.ocrbench

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.sza.fastmediasorter.ui.player.views.TranslationOverlayView

/**
 * S1782: compose the shipped [TranslationOverlayView] over a bench scene and hand back real pixels.
 *
 * The three axes S1716 measures are rectangles, and the fourth - whether the plate actually hides the
 * source text - cannot be read off a rectangle. This calls the shipped view rather than re-deriving its
 * drawing: a bench carrying its own copy of the arithmetic would score its approximation of the app,
 * which is the error strategic §4 refuses.
 */
object OverlayRasterizer {

    /**
     * A composition that did not happen.
     *
     * An exception rather than a returned bitmap on purpose: a raster equal to its input carries
     * residual ink 0, which is also the value of perfect concealment, and S1716 §2.4 exists precisely
     * so those two must never look alike.
     */
    class RasterFailure(message: String) : IllegalStateException(message)

    // The recogniser's confidence is not an input to this bench - every annotated area is treated as a
    // certain read, because the corpus author wrote the box by hand rather than sampling a detector.
    private const val FULL_CONFIDENCE = 1f

    /**
     * Draw [scene]'s overlay onto a copy of its bitmap and return the copy.
     *
     * The view is laid out at the scene's own pixel size, so nothing scales between what the app draws
     * and what the axis later measures. The corpus bitmap is never drawn into: S1716 §11 criterion 4
     * requires a scene to stay reproducible, and a scene mutated by its first reader is not.
     */
    fun compose(scene: SyntheticScene.Built, context: Context): Bitmap {
        val annotation = scene.annotation
        val width = annotation.widthPx
        val height = annotation.heightPx

        val view = TranslationOverlayView(context)
        view.setSourceBitmap(scene.bitmap)
        view.setOriginalImageSize(width, height)
        view.setScale(width, height, width, height)
        view.updateImageDisplayRect(RectF(0f, 0f, width.toFloat(), height.toFloat()))
        view.setTranslatedBlocks(annotation.textAreas.map(::toTranslatedBlock))

        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)

        val composition = scene.bitmap.copy(scene.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        view.draw(Canvas(composition))

        if (composition.sameAs(scene.bitmap)) {
            throw RasterFailure(
                "scene '${annotation.sceneId}': the composition is byte-equal to its input, so the " +
                    "graphics mode rasterised nothing. A blank result is unmeasured, not concealment."
            )
        }
        requireOverlayReachedItsAreas(annotation, scene.bitmap, composition)
        return composition
    }

    /**
     * The whole-bitmap comparison above is necessary but NOT sufficient, and this is why.
     *
     * [TranslationOverlayView.onDraw] ends with a `BuildConfig.DEBUG` block that strokes the image-display
     * rect and a crosshair (S1702). The unit suite runs the debug variant, so that frame lands on every
     * composition even when the overlay placed no plate at all - which means `sameAs` on its own can never
     * report "nothing was drawn". Strategic §7 names exactly this as the risk of the silent zero returning
     * in a new guise, so the guard asks the question that actually matters: did the overlay change pixels
     * INSIDE the areas it was handed.
     */
    private fun requireOverlayReachedItsAreas(
        annotation: SceneAnnotation,
        scene: Bitmap,
        composition: Bitmap,
    ) {
        if (annotation.textAreas.isEmpty()) {
            throw RasterFailure(
                "scene '${annotation.sceneId}': the annotation carries no text area, so no region " +
                    "exists that a plate could cover. That is unmeasured, not perfect concealment."
            )
        }
        val reached = annotation.textAreas.any { area -> differsInside(scene, composition, area.box) }
        if (!reached) {
            throw RasterFailure(
                "scene '${annotation.sceneId}': every annotated area came back byte-identical, so the " +
                    "overlay drew nothing where it was asked to and only the debug frame changed."
            )
        }
    }

    /** True when any pixel inside [box] differs between the scene and its composition. */
    private fun differsInside(scene: Bitmap, composition: Bitmap, box: Rect): Boolean {
        val clipped = Rect(box)
        if (!clipped.intersect(0, 0, scene.width, scene.height)) {
            return false
        }
        return (clipped.top until clipped.bottom).any { y ->
            (clipped.left until clipped.right).any { x ->
                scene.getPixel(x, y) != composition.getPixel(x, y)
            }
        }
    }

    /**
     * One annotated area as the view expects it.
     *
     * The translated string is the area's own text on purpose: the concealment axis measures what
     * survives UNDER the plate, and inventing different wording would change the plate's size for a
     * reason the axis cannot see. The box is copied because [Rect] is mutable and the view keeps it.
     */
    private fun toTranslatedBlock(area: TextArea): TranslationOverlayView.TranslatedBlock =
        TranslationOverlayView.TranslatedBlock(
            originalText = area.text,
            translatedText = area.text,
            boundingBox = Rect(area.box),
            confidence = FULL_CONFIDENCE,
        )
}
