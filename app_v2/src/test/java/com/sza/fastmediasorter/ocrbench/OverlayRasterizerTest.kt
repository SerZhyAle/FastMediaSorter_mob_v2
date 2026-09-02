package com.sza.fastmediasorter.ocrbench

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S1782: proves both halves of strategic §11 criterion 3 - that the overlay really rasterises, and that a
 * composition which drew nothing is refused rather than scored.
 *
 * The second half is the one a passing suite would otherwise never exercise, because every corpus scene
 * draws something; without it the guard could rot into a branch nobody ever takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayRasterizerTest {

    // RuntimeEnvironment, not ApplicationProvider: androidx.test:core is an androidTest dependency here,
    // so the instrumentation helper does not exist on the unit-test classpath.
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `every corpus scene composes to a raster that differs from its input`() {
        SyntheticScene.all().forEach { scene ->
            val composition = OverlayRasterizer.compose(scene, context)
            assertFalse(
                "scene '${scene.annotation.sceneId}' composed to a bitmap equal to its input",
                composition.sameAs(scene.bitmap),
            )
        }
    }

    @Test
    fun `a scene with no annotated area is refused rather than returned unchanged`() {
        assertThrows(OverlayRasterizer.RasterFailure::class.java) {
            OverlayRasterizer.compose(sceneWithNoAreas(), context)
        }
    }

    /**
     * A scene the overlay has nothing to draw over: its annotation carries no text area, so the view paints
     * no plate and the composition comes back byte-equal - the exact case the guard exists to refuse.
     */
    private fun sceneWithNoAreas(): SyntheticScene.Built {
        val bitmap = Bitmap.createBitmap(BLANK_WIDTH, BLANK_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        return SyntheticScene.Built(
            bitmap = bitmap,
            annotation = SceneAnnotation(
                version = SceneAnnotation.CURRENT_VERSION,
                sceneId = "no-annotated-area",
                widthPx = BLANK_WIDTH,
                heightPx = BLANK_HEIGHT,
                textAreas = emptyList(),
                paintableAreas = emptyList(),
                readable = true,
                provenance = Provenance(
                    author = "S1782 raster proof",
                    annotatedOn = "2026-08-25",
                    draft = false,
                ),
            ),
        )
    }

    companion object {
        private const val BLANK_WIDTH = 320
        private const val BLANK_HEIGHT = 240
    }
}
