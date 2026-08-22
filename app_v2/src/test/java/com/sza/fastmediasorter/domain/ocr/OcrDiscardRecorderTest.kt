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
 * S1712: the record exists so that "the engine found nothing" and "we threw away four correct captions"
 * stop looking identical (strategic §1). These cases are the ones that claim can be checked by.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrDiscardRecorderTest {

    private fun block(text: String, confidence: Float = 90f, width: Int = 200, height: Int = 40) =
        OcrTextBlock(text, Rect(0, 0, width, height), confidence)

    @Test
    fun `an off channel records nothing and keeps no counter`() {
        val recorder = OcrDiscardRecorder()
        recorder.beginRun()

        recorder.record(block("noise", confidence = 5f), OcrBlockFilter.Verdict.LOW_CONFIDENCE)
        recorder.record(block("Hello world"), OcrBlockFilter.Verdict.ACCEPTED)

        assertFalse(recorder.isEnabled())
        assertTrue(recorder.lastRun.isEmpty())
        assertEquals(0, recorder.lastAcceptedCount)
    }

    @Test
    fun `an on channel names the failed condition per fragment`() {
        val recorder = OcrDiscardRecorder().apply { setEnabled(true) }
        recorder.beginRun()

        recorder.record(block("noise", confidence = 5f), OcrBlockFilter.Verdict.LOW_CONFIDENCE)
        recorder.record(block("::"), OcrBlockFilter.Verdict.TOO_MANY_SPECIAL_CHARS)

        assertEquals(2, recorder.lastRun.size)
        assertEquals(OcrBlockFilter.Verdict.LOW_CONFIDENCE, recorder.lastRun[0].verdict)
        assertEquals(OcrBlockFilter.Verdict.TOO_MANY_SPECIAL_CHARS, recorder.lastRun[1].verdict)
    }

    @Test
    fun `a page where everything was discarded still produces records`() {
        val recorder = OcrDiscardRecorder().apply { setEnabled(true) }
        recorder.beginRun()

        repeat(4) { recorder.record(block("caption", confidence = 10f), OcrBlockFilter.Verdict.LOW_CONFIDENCE) }

        assertEquals(0, recorder.lastAcceptedCount)
        assertEquals(4, recorder.lastRun.size)
        assertEquals(mapOf(OcrBlockFilter.Verdict.LOW_CONFIDENCE to 4), recorder.countsByVerdict())
    }

    @Test
    fun `accepted fragments move the counter and are not kept`() {
        val recorder = OcrDiscardRecorder().apply { setEnabled(true) }
        recorder.beginRun()

        recorder.record(block("Hello world"), OcrBlockFilter.Verdict.ACCEPTED)
        recorder.record(block("Hello again"), OcrBlockFilter.Verdict.ACCEPTED)

        assertEquals(2, recorder.lastAcceptedCount)
        assertTrue(recorder.lastRun.isEmpty())
    }

    @Test
    fun `a new run replaces the previous one`() {
        val recorder = OcrDiscardRecorder().apply { setEnabled(true) }
        recorder.beginRun()
        recorder.record(block("first", confidence = 1f), OcrBlockFilter.Verdict.LOW_CONFIDENCE)

        recorder.beginRun()
        recorder.record(block("second", confidence = 2f), OcrBlockFilter.Verdict.LOW_CONFIDENCE)

        assertEquals(1, recorder.lastRun.size)
        assertEquals("second", recorder.lastRun.single().text)
    }

    @Test
    fun `turning the channel off drops what was collected`() {
        val recorder = OcrDiscardRecorder().apply { setEnabled(true) }
        recorder.beginRun()
        recorder.record(block("caption", confidence = 3f), OcrBlockFilter.Verdict.LOW_CONFIDENCE)

        recorder.setEnabled(false)

        assertTrue(recorder.lastRun.isEmpty())
        assertEquals(0, recorder.lastAcceptedCount)
    }

    @Test
    fun `a record line carries the verdict, the confidence, the box and the text`() {
        val recorder = OcrDiscardRecorder().apply { setEnabled(true) }
        recorder.beginRun()
        recorder.record(
            block("caption", confidence = 12.5f, width = 30, height = 8),
            OcrBlockFilter.Verdict.BOX_TOO_SMALL
        )

        val line = recorder.lastRun.single().toLine()

        assertTrue(line, line.contains("BOX_TOO_SMALL"))
        assertTrue(line, line.contains("conf=12.5"))
        assertTrue(line, line.contains("30x8"))
        assertTrue(line, line.contains("caption"))
    }
}
