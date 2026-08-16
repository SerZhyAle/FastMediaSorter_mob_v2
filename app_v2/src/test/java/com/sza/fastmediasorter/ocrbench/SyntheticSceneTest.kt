package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1716: a scene that drifts between runs turns every later measurement into noise, so its stability is the
 * first thing the bench has to prove about itself (strategic §11 criterion 4).
 *
 * **What this class can and cannot assert today.** Robolectric runs here in its default legacy graphics
 * mode, which records drawing calls without rasterising them: shapes come back described, and a scene built
 * only from text comes back byte-identical to a blank bitmap. Switching the class to
 * `@GraphicsMode(NATIVE)` was tried on 2026-08-16 and failed at class-init with
 * `NoClassDefFoundError: android.graphics.ColorSpace` - the native runtime is not on this project's test
 * classpath. Until that is decided (strategic §6.1 is re-opened with the measurement), the pixel-level
 * assertion that a scene is not blank cannot be made from a unit test, and it is deliberately absent rather
 * than weakened into something that would pass on an empty canvas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyntheticSceneTest {

    @Test
    fun `every scene redraws pixel-identically`() {
        for (built in SyntheticScene.all()) {
            val again = SyntheticScene.all().first { it.annotation.sceneId == built.annotation.sceneId }

            assertTrue(
                "scene ${built.annotation.sceneId} is not stable between renders",
                built.bitmap.sameAs(again.bitmap)
            )
        }
    }

    @Test
    fun `every scene declares both kinds of area`() {
        for (built in SyntheticScene.all()) {
            val id = built.annotation.sceneId
            assertTrue("$id has no text area", built.annotation.textAreas.isNotEmpty())
            assertTrue("$id has no paintable area", built.annotation.paintableAreas.isNotEmpty())
        }
    }

    @Test
    fun `every scene is scorable and carries the current format version`() {
        for (built in SyntheticScene.all()) {
            assertTrue(built.annotation.sceneId, built.annotation.isScorable())
            assertEquals(SceneAnnotation.CURRENT_VERSION, built.annotation.version)
        }
    }

    @Test
    fun `a draft annotation is refused for scoring`() {
        val base = SyntheticScene.lineWithTallArtifact().annotation
        val draft = base.copy(provenance = base.provenance.copy(draft = true))

        assertFalse(draft.isScorable())
    }

    @Test
    fun `an unreadable scene is refused for scoring`() {
        val base = SyntheticScene.darkPanel().annotation

        assertFalse(base.copy(readable = false).isScorable())
    }

    @Test
    fun `an annotation without text areas is refused for scoring`() {
        val base = SyntheticScene.darkPanel().annotation

        assertFalse(base.copy(textAreas = emptyList()).isScorable())
    }

    @Test
    fun `the artifact scene keeps the artifact outside the paintable area`() {
        val built = SyntheticScene.lineWithTallArtifact()
        val paintable = built.annotation.paintableAreas.single().box
        val text = built.annotation.textAreas.single().box

        assertTrue("the text must sit inside the paintable area", paintable.contains(text))
        assertTrue("the scene must be wider than what may be painted", built.annotation.widthPx > paintable.right)
    }

    /**
     * The pixel-level "it actually drew" assertion belongs here and is missing on purpose - see the class
     * KDoc. What can be checked without rasterisation is that a scene declares the size it claims, which at
     * least catches a builder that returned someone else's bitmap.
     */
    @Test
    fun `every scene bitmap has the size its annotation declares`() {
        for (built in SyntheticScene.all()) {
            assertEquals(built.annotation.sceneId, built.annotation.widthPx, built.bitmap.width)
            assertEquals(built.annotation.sceneId, built.annotation.heightPx, built.bitmap.height)
        }
    }

    @Test
    fun `the uniform scene keeps its lines apart`() {
        val boxes = SyntheticScene.uniformMultilineText().annotation.textAreas.map { it.box }

        for (index in 1 until boxes.size) {
            assertTrue(
                "line $index overlaps the one above it",
                boxes[index].top > boxes[index - 1].bottom
            )
        }
        assertEquals(boxes.size, boxes.distinctBy { Rect(it) }.size)
    }
}
