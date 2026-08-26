package com.sza.fastmediasorter.ocrbench

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S1782: holds the one distinction the whole ticket exists for - "not measured" is not "nothing
 * visible".
 *
 * Strategic §2 goal 2 states it, and a statement in a spec is not a property of the code. It became a
 * property when these tests were written, and it stops being one the moment they are deleted: the
 * neighbouring project's wrong aggregate came from exactly this confusion, an unrendered scene arriving
 * as residual ink `0` and averaging in as a perfect result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConcealmentMetricTest {

    // RuntimeEnvironment, not ApplicationProvider: androidx.test:core is an androidTest dependency here,
    // so the instrumentation helper does not exist on the unit-test classpath.
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `a plate over its own line leaves a stated residual and says it measured`() {
        val scene = SyntheticScene.lineInSmallCapitals()
        val composition = OverlayRasterizer.compose(scene, context)
        val control = OverlayRasterizer.compose(ConcealmentMetric.withoutSourceInk(scene), context)
        val result = ConcealmentMetric.score(scene, composition, control)

        assertTrue("the scene is ground truth, so it must be measured", result.isMeasured)
        val worst = (result.worstResidualInk as Measured.Value).value
        assertTrue(
            "a plate covering its own line left residual ink $worst",
            worst <= MAX_RESIDUAL_UNDER_A_COVERING_PLATE,
        )
    }

    @Test
    fun `a draft annotation returns no number at all`() {
        val scene = draftScene()
        val result = ConcealmentMetric.score(scene, scene.bitmap, scene.bitmap)

        assertTrue("a draft annotation must not be scored", !result.isMeasured)
        assertNull(
            "an unmeasured scene must carry no value",
            (result.worstResidualInk as? Measured.Value)?.value,
        )
        assertTrue(
            "the refusal must say why: ${result.unmeasuredReason()}",
            result.unmeasuredReason()?.contains("draft") == true,
        )
    }

    @Test
    fun `an unmeasured scene is counted in its own field and never enters the aggregate`() {
        val measured = SceneConcealment(
            sceneId = "measured-scene",
            areas = emptyList(),
            worstResidualInk = Measured.Value(KNOWN_RESIDUAL),
        )
        val refused = ConcealmentMetric.unmeasured("refused-scene", "the rasterizer refused it")

        val summary = ConcealmentReport.summarise(listOf(measured, refused))

        assertEquals("both scenes stay in the total", 2, summary.sceneCount)
        assertEquals(1, summary.measuredCount)
        assertEquals(1, summary.unmeasuredCount)
        // Folding the refusal in as a zero would drag both of these to half the real value - the exact
        // arithmetic that produced a neighbouring project's wrong aggregate.
        assertEquals(KNOWN_RESIDUAL, summary.worst ?: 0.0, EXACT)
        assertEquals(KNOWN_RESIDUAL, summary.median ?: 0.0, EXACT)
        assertTrue("the reason must survive into the report", summary.unmeasuredReasons.isNotEmpty())
    }

    @Test
    fun `the corpus runs and writes a dated concealment report`() {
        val scenes = ConcealmentReport.run(context)
        val summary = ConcealmentReport.summarise(scenes)
        val report = ConcealmentReport.write(ConcealmentReport.render(scenes, summary))

        assertTrue("the report must exist after the run", report.isFile)
        assertEquals(
            "every scene is either measured or counted as not measured",
            summary.sceneCount,
            summary.measuredCount + summary.unmeasuredCount,
        )
    }

    /**
     * A scene whose annotation is recogniser-filled. Its pixels are irrelevant on purpose: the refusal
     * has to come from the annotation, before a single pixel is read.
     */
    private fun draftScene(): SyntheticScene.Built {
        val bitmap = Bitmap.createBitmap(DRAFT_WIDTH, DRAFT_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        return SyntheticScene.Built(
            bitmap = bitmap,
            annotation = SceneAnnotation(
                version = SceneAnnotation.CURRENT_VERSION,
                sceneId = "draft-annotation",
                widthPx = DRAFT_WIDTH,
                heightPx = DRAFT_HEIGHT,
                textAreas = listOf(TextArea("drafted", Rect(0, 0, DRAFT_WIDTH, DRAFT_HEIGHT))),
                paintableAreas = emptyList(),
                readable = true,
                provenance = Provenance(
                    author = "S1782 concealment axis",
                    annotatedOn = "2026-08-26",
                    draft = true,
                ),
            ),
        )
    }

    companion object {
        private const val DRAFT_WIDTH = 320
        private const val DRAFT_HEIGHT = 240

        /**
         * Read off the dated report of 2026-08-26, not chosen: that run measured
         * `line-in-small-capitals` at 0.0736. The bound is one unit of the report's own print
         * precision above it - four decimal places - and that single unit is the only slack here,
         * not headroom anyone budgeted. The corpus-wide bound lives in
         * `app_v2/src/test/resources/ocrbench/concealment-bounds.json` and names the same report.
         */
        private const val MAX_RESIDUAL_UNDER_A_COVERING_PLATE = 0.0737

        /** An arbitrary distinct value - the test is about which scenes reach the aggregate. */
        private const val KNOWN_RESIDUAL = 0.4
        private const val EXACT = 0.0
    }
}
