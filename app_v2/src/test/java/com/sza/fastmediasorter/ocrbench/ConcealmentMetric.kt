package com.sza.fastmediasorter.ocrbench

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/**
 * S1782: the fourth axis - how much of the source text is still visible after the plate drew.
 *
 * The three axes S1716 measures are arithmetic on boxes, so none of them can answer this one: a plate
 * whose rectangle covers a line perfectly still fails the reader if it is translucent.
 *
 * **How a surviving letter is told apart from a repainted one.** Asking "did this pixel change" does
 * not work, and both ways it fails are live in this app. A plate at 94 % alpha changes every pixel it
 * covers while the letter underneath stays perfectly readable, so a byte comparison would call that
 * concealed. And the plate redraws text of its own on top, so a pixel the translation happens to paint
 * in the source's colour would be called unchanged - surviving ink - when nothing of the source is
 * left. Measured on 2026-08-26: the first definition tried here was byte equality, and it reported
 * 0.0168-0.0728 on a corpus whose plates are 94 % translucent, a number that answers neither question.
 *
 * So the axis is measured against a **control composition** - the same scene with its source ink
 * erased, drawn by the same view. Everything the overlay paints is identical in both, and whatever
 * still differs between them is the source showing through. The residual is that surviving difference
 * as a share of the contrast the source had to begin with, so a fully opaque plate scores 0, a plate
 * at 94 % alpha scores about 0.06, and no plate at all scores 1.
 *
 * **There is no tuned constant in this file, and no bound.** Strategic §5.1 pillar 4 keeps the
 * acceptance bound out of the code that produces the number, because a bound living beside its own
 * metric can be nudged into agreement with it and the nudge disappears into the diff.
 */
object ConcealmentMetric {

    /**
     * Residual ink for every annotated area of [scene], read off [composition] against [control].
     *
     * [control] must be the composition of the same scene with its source ink erased - see
     * [withoutSourceInk]. Passing the scene itself would silently turn this back into the byte
     * comparison the class comment records as wrong.
     *
     * A scene the annotation refuses to score returns no number at all - not a zero. S1716 §2.4 is the
     * whole reason: residual ink `0` is also the value of perfect concealment, so the two must never be
     * able to arrive in the same shape.
     */
    fun score(scene: SyntheticScene.Built, composition: Bitmap, control: Bitmap): SceneConcealment {
        val annotation = scene.annotation
        if (!annotation.isScorable()) {
            return unmeasured(annotation.sceneId, refusalReason(annotation))
        }
        val areas = annotation.textAreas.mapIndexed { index, area ->
            scoreArea(scene.bitmap, composition, control, area, index)
        }
        val values = areas.mapNotNull { (it.residualInk as? Measured.Value)?.value }
        val worst = if (values.isEmpty()) {
            Measured.Unmeasured(NO_AREA_MEASURED)
        } else {
            Measured.Value(values.max())
        }
        return SceneConcealment(annotation.sceneId, areas, worst)
    }

    /** A scene whose composition never happened, or whose annotation refuses to be ground truth. */
    fun unmeasured(sceneId: String, reason: String): SceneConcealment =
        SceneConcealment(sceneId, emptyList(), Measured.Unmeasured(reason))

    /**
     * The same scene with the ink inside every annotated area flattened to that area's own background.
     *
     * The annotation is carried through untouched, which is what makes the control comparable: the view
     * derives the plate's size from the box and the type metrics, so an erased scene produces a plate of
     * the same geometry in the same place. Its colour also survives, because the view samples one pixel
     * at the box's top-left corner and that corner is background, not glyph - if that ever stops being
     * true the residual jumps toward 1 for every scene at once, which is loud rather than silent.
     */
    fun withoutSourceInk(scene: SyntheticScene.Built): SyntheticScene.Built {
        val erased = scene.bitmap.copy(scene.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        for (area in scene.annotation.textAreas) {
            val box = Rect(area.box)
            if (!box.intersect(0, 0, erased.width, erased.height)) {
                continue
            }
            val background = backgroundOf(scene.bitmap, box)
            forEachPixel(box) { x, y ->
                erased.setPixel(x, y, background)
            }
        }
        return SyntheticScene.Built(bitmap = erased, annotation = scene.annotation)
    }

    /**
     * Why [SceneAnnotation.isScorable] said no.
     *
     * The rule itself is not restated here - it is read from the annotation, which is where S1716
     * authored it. Only the wording of the refusal lives here, because a report that prints
     * "not scorable" tells its reader nothing they can act on.
     */
    private fun refusalReason(annotation: SceneAnnotation): String = when {
        annotation.provenance.draft -> DRAFT_ANNOTATION
        !annotation.readable -> UNREADABLE_SCENE
        else -> NO_TEXT_AREA
    }

    private fun scoreArea(
        scene: Bitmap,
        composition: Bitmap,
        control: Bitmap,
        area: TextArea,
        index: Int,
    ): AreaConcealment {
        val box = Rect(area.box)
        val onScreen = box.intersect(0, 0, scene.width, scene.height)
        val background = if (onScreen) backgroundOf(scene, box) else Color.TRANSPARENT
        var sourceContrast = 0f
        var survivingContrast = 0f
        if (onScreen) {
            forEachPixel(box) { x, y ->
                val ink = contrast(scene.getPixel(x, y), background)
                sourceContrast += ink
                if (ink > NO_CONTRAST) {
                    survivingContrast += contrast(composition.getPixel(x, y), control.getPixel(x, y))
                }
            }
        }
        val refusal = when {
            !onScreen -> AREA_OFF_SCENE
            sourceContrast == NO_CONTRAST -> AREA_CARRIES_NO_INK
            else -> null
        }
        if (refusal != null) {
            return AreaConcealment(index, area.text, Measured.Unmeasured(refusal))
        }
        return AreaConcealment(
            index,
            area.text,
            Measured.Value((survivingContrast / sourceContrast).toDouble()),
        )
    }

    /**
     * How far apart two colours are to an eye, via the platform's own relative luminance.
     *
     * [Color.luminance] rather than a hand-written weighting so no coefficient is authored here that a
     * later reader would have to take on trust.
     */
    private fun contrast(first: Int, second: Int): Float =
        kotlin.math.abs(Color.luminance(first) - Color.luminance(second))

    /**
     * The colour most of the area already had before the overlay drew.
     *
     * Modal rather than sampled from a corner: a text box is mostly its own background, and a corner
     * can legitimately hold a glyph. This is what makes the metric work unchanged on the dark-panel
     * scene, where an absolute "ink is dark" rule would report the whole panel as ink.
     */
    private fun backgroundOf(scene: Bitmap, box: Rect): Int {
        val counts = HashMap<Int, Int>()
        forEachPixel(box) { x, y ->
            counts.merge(scene.getPixel(x, y), 1, Int::plus)
        }
        // The box is non-empty at every call site, so the fallback is unreachable rather than a default.
        return counts.maxByOrNull { it.value }?.key ?: Color.TRANSPARENT
    }

    private inline fun forEachPixel(box: Rect, action: (Int, Int) -> Unit) {
        for (y in box.top until box.bottom) {
            for (x in box.left until box.right) {
                action(x, y)
            }
        }
    }

    /** Zero, named. Not a tolerance: it is the value that means "this pixel is its own background". */
    private const val NO_CONTRAST = 0f

    private const val DRAFT_ANNOTATION =
        "the annotation is a recogniser-filled draft, so scoring it would measure the recogniser"
    private const val UNREADABLE_SCENE =
        "a human cannot read this scene, so there is no source text to conceal"
    private const val NO_TEXT_AREA =
        "the annotation carries no text area, so no region exists that a plate could cover"
    private const val NO_AREA_MEASURED =
        "every annotated area of this scene went unmeasured"
    private const val AREA_OFF_SCENE =
        "the annotated area falls entirely outside the scene bitmap"
    private const val AREA_CARRIES_NO_INK =
        "the annotated area is one flat colour, so it carries no ink to conceal"
}

/**
 * One annotated area's residual ink: the share of the source's own contrast that still shows through
 * everything the overlay drew. 0 is a plate nothing reads through, 1 is no plate at all.
 *
 * [residualInk] is a [Measured], not a nullable number, so "we did not measure" cannot be spelled the
 * same way as "we measured zero". [isMeasured] reads that decision off the value rather than storing
 * it a second time - a separate boolean field would allow the one combination that must not exist, a
 * number marked unmeasured.
 */
data class AreaConcealment(
    val areaIndex: Int,
    val text: String,
    val residualInk: Measured<Double>,
) {
    val isMeasured: Boolean get() = residualInk is Measured.Value
}

/**
 * One scene's concealment result. [worstResidualInk] is the highest residual over the areas that were
 * measured, because the axis is about the line a reader can still make out, not about the average line.
 */
data class SceneConcealment(
    val sceneId: String,
    val areas: List<AreaConcealment>,
    val worstResidualInk: Measured<Double>,
) {
    val isMeasured: Boolean get() = worstResidualInk is Measured.Value

    /** The reason this scene carries no number, or null when it carries one. */
    fun unmeasuredReason(): String? = (worstResidualInk as? Measured.Unmeasured)?.reason
}
