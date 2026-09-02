package com.sza.fastmediasorter.ocrbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S2036: the annotation reader across format versions.
 *
 * Version 1 predates word geometry. It has to keep loading, because the alternative - re-annotating every
 * scene before the word axes work at all - is what pushes an author toward filling the words from a
 * recogniser's own output, and scoring a recogniser against itself measures nothing.
 *
 * The fixtures are ordinary annotation resources and reach the corpus through nothing: [RealSceneSource.all]
 * walks the manifest, never the annotation directory, and the manifest asks for no scene.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealSceneSourceTest {

    @Test
    fun `a version one annotation loads and carries no word`() {
        val annotation = RealSceneSource.annotationFor("fixture-version-one")

        assertEquals(1, annotation.version)
        assertTrue(annotation.isScorable())
        assertTrue(
            "a version-1 annotation cannot carry word geometry",
            annotation.textAreas.all { it.words.isEmpty() }
        )
    }

    @Test
    fun `a version two annotation loads its words`() {
        val area = RealSceneSource.annotationFor("fixture-version-two").textAreas.single()

        assertEquals(listOf("Big", "xx"), area.words.map { it.text })
        assertEquals(listOf(30, 18), area.words.map { it.box.height() })
        assertTrue("every word must sit inside its line", area.words.all { area.box.contains(it.box) })
    }

    @Test
    fun `a version outside the supported set is refused, and the message names the set`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            RealSceneSource.annotationFor("fixture-unsupported-version")
        }

        val message = failure.message.orEmpty()
        assertTrue("the refusal must name the version found: $message", message.contains("99"))
        assertTrue("the refusal must name the supported set: $message", message.contains("1, 2"))
    }

    @Test
    fun `an absent annotation names the script that cannot fix it`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            RealSceneSource.annotationFor("no-such-scene")
        }

        assertTrue(failure.message.orEmpty().contains(RealSceneSource.FETCH_SCRIPT))
    }
}
