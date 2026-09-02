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
 * **What this class asserts, and why it stays on legacy graphics.** Robolectric runs here in its default
 * legacy mode, which records drawing calls without rasterising them: a scene built only from text comes back
 * byte-identical to a blank bitmap. That is exactly why the assertions here are about *stability* - the same
 * description twice - and not about pixels.
 *
 * The class-init failure this comment used to describe is fixed and the note is kept only so nobody re-files
 * it: `@GraphicsMode(NATIVE)` failed on 2026-08-16 under `robolectric:4.11.1`, whose native-runtime
 * distribution ships no Windows binary at all. S1782 raised the pin to **4.16.1**, which does, and
 * `OverlayRasterizerTest` and `ConcealmentMetricTest` in this package now run NATIVE and rasterise for real.
 * Strategic §6.1 is resolved, not re-opened. This class is deliberately left on legacy graphics: a
 * reproducibility check does not need a rasteriser, and running it without one keeps it honest about what it
 * is proving.
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

    /**
     * S2036: an authoring slip here would surface in the report as an implausible height ratio and be read
     * as a property of the material, so containment is asserted rather than eyeballed.
     */
    @Test
    fun `the varied-height scene keeps every word inside its own line`() {
        val area = SyntheticScene.lineWithVariedWordHeights().annotation.textAreas.single()

        assertTrue("the scene must annotate several words", area.words.size > 1)
        for (word in area.words) {
            assertTrue("word '${word.text}' escapes its line box", area.box.contains(word.box))
        }
        assertTrue(
            "the line box must be taller than every word, the way a real line box is",
            area.words.all { it.box.height() < area.box.height() }
        )
        assertTrue(
            "the words must differ in height or the axes have nothing to separate",
            area.words.map { it.box.height() }.distinct().size > 1
        )
    }

    /** S2036: an area nobody annotated at word level must stay distinguishable from one holding no word. */
    @Test
    fun `scenes annotated before word geometry carry no words`() {
        val area = SyntheticScene.uniformMultilineText().annotation.textAreas.first()

        assertTrue("this scene is line-only by construction", area.words.isEmpty())
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
