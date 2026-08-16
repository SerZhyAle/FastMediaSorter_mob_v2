package com.sza.fastmediasorter.domain.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1712: the discard verdict, which is the whole point of the ticket - a rejected fragment has to say
 * which of the four thresholds it failed, not just that it failed (strategic §11 criterion 2).
 *
 * These are the only place the thresholds are asserted. A second copy of any of them elsewhere in the
 * tree would pass its own tests and diverge from this one at the first edit, which is the failure
 * ADR-1 exists to prevent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrBlockFilterTest {

    @Test
    fun `a good fragment is accepted`() {
        val verdict = OcrBlockFilter.evaluate(block(text = "Hello world", confidence = 90f))

        assertEquals(OcrBlockFilter.Verdict.ACCEPTED, verdict)
        assertTrue(OcrBlockFilter.isAccepted(block(text = "Hello world", confidence = 90f)))
    }

    @Test
    fun `confidence below the threshold names the confidence condition`() {
        val verdict = OcrBlockFilter.evaluate(block(text = "Hello world", confidence = 29f))

        assertEquals(OcrBlockFilter.Verdict.LOW_CONFIDENCE, verdict)
    }

    @Test
    fun `confidence exactly at the threshold passes`() {
        val verdict = OcrBlockFilter.evaluate(block(text = "Hello world", confidence = 30f))

        assertEquals(OcrBlockFilter.Verdict.ACCEPTED, verdict)
    }

    @Test
    fun `text shorter than the minimum names the length condition`() {
        val verdict = OcrBlockFilter.evaluate(block(text = "  ab  ", confidence = 90f))

        assertEquals(OcrBlockFilter.Verdict.TEXT_TOO_SHORT, verdict)
    }

    @Test
    fun `punctuation-heavy text names the special-character condition`() {
        val verdict = OcrBlockFilter.evaluate(block(text = "a!!!!", confidence = 90f))

        assertEquals(OcrBlockFilter.Verdict.TOO_MANY_SPECIAL_CHARS, verdict)
    }

    @Test
    fun `text with no letters at all fails the ratio rather than dividing by zero`() {
        val verdict = OcrBlockFilter.evaluate(block(text = "1234", confidence = 90f))

        assertEquals(OcrBlockFilter.Verdict.TOO_MANY_SPECIAL_CHARS, verdict)
    }

    @Test
    fun `a narrow box names the box condition`() {
        val verdict = OcrBlockFilter.evaluate(
            block(text = "Hello world", confidence = 90f, right = 19, bottom = 40),
        )

        assertEquals(OcrBlockFilter.Verdict.BOX_TOO_SMALL, verdict)
    }

    @Test
    fun `a short box names the box condition`() {
        val verdict = OcrBlockFilter.evaluate(
            block(text = "Hello world", confidence = 90f, right = 200, bottom = 9),
        )

        assertEquals(OcrBlockFilter.Verdict.BOX_TOO_SMALL, verdict)
    }

    @Test
    fun `a fragment failing several conditions reports the first one applied`() {
        // Low confidence AND too short: the pipeline used to stop at confidence, and the recorded
        // reason must match what the pipeline actually decided on.
        val verdict = OcrBlockFilter.evaluate(block(text = "a", confidence = 1f))

        assertEquals(OcrBlockFilter.Verdict.LOW_CONFIDENCE, verdict)
    }

    @Test
    fun `isAccepted agrees with evaluate on every rejection`() {
        val rejected = listOf(
            block(text = "Hello world", confidence = 10f),
            block(text = "ab", confidence = 90f),
            block(text = "a!!!!", confidence = 90f),
            block(text = "Hello world", confidence = 90f, right = 5, bottom = 5),
        )

        rejected.forEach { block ->
            assertFalse(OcrBlockFilter.isAccepted(block))
            assertTrue(OcrBlockFilter.evaluate(block) != OcrBlockFilter.Verdict.ACCEPTED)
        }
    }

    private fun block(
        text: String,
        confidence: Float,
        right: Int = 200,
        bottom: Int = 40,
    ) = OcrTextBlock(
        text = text,
        boundingBox = Rect(0, 0, right, bottom),
        confidence = confidence,
    )
}
