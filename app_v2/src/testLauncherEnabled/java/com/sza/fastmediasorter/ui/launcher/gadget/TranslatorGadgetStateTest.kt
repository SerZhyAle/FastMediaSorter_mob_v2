package com.sza.fastmediasorter.ui.launcher.gadget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S1177 phase 04: which state the translator cell shows, asserted without a view or an engine.
 *
 * These five outcomes are the promise the cell makes - strategic §2 goal 4 requires it to say what is
 * happening rather than look broken - and a promise checkable only by hand on a device is one that stops
 * holding without anyone noticing.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
class TranslatorGadgetStateTest {

    @Test
    fun `blank input reports itself before anything else`() {
        assertEquals(
            TranslatorState.EMPTY_INPUT,
            decideTranslatorState(input = "   ", translated = null, modelMissing = true, failed = true),
        )
    }

    /**
     * The missing pack outranks a failure: it is the one state with a way forward, and reporting the
     * failure instead would send the user looking for a problem that resolves itself.
     */
    @Test
    fun `a missing language pack outranks a failure`() {
        assertEquals(
            TranslatorState.MODEL_MISSING,
            decideTranslatorState(input = "hello", translated = null, modelMissing = true, failed = true),
        )
    }

    @Test
    fun `an engine failure is reported as a failure`() {
        assertEquals(
            TranslatorState.FAILED,
            decideTranslatorState(input = "hello", translated = null, modelMissing = false, failed = true),
        )
    }

    /**
     * No translation and no failure means the engine answered and had nothing for this pair - offline
     * translation does not cover every combination, and that is not an error to apologise for.
     */
    @Test
    fun `a silent null is an unavailable pair, not a failure`() {
        assertEquals(
            TranslatorState.PAIR_UNAVAILABLE,
            decideTranslatorState(input = "hello", translated = null, modelMissing = false, failed = false),
        )
    }

    @Test
    fun `a translation reports the translated state`() {
        assertEquals(
            TranslatorState.TRANSLATED,
            decideTranslatorState(input = "hello", translated = "привет", modelMissing = false, failed = false),
        )
    }

    /**
     * An empty translation is still a translation: the engine can legitimately return an empty string for
     * input that carries no words, and treating it as a failure would blame the user's punctuation.
     */
    @Test
    fun `an empty translation is not a failure`() {
        assertEquals(
            TranslatorState.TRANSLATED,
            decideTranslatorState(input = "...", translated = "", modelMissing = false, failed = false),
        )
    }

    /**
     * S2988: the call that downloads the pack and then translates still carries the download latch, so
     * ranking the latch first captioned a visible translation with "the pack is still downloading".
     */
    @Test
    fun `a translation outranks the pending language pack`() {
        assertEquals(
            TranslatorState.TRANSLATED,
            decideTranslatorState(input = "hello", translated = "привет", modelMissing = true, failed = false),
        )
    }

    /**
     * S2732: the in-flight state says the engine has not answered yet, so only the view - which knows it
     * just started a call - may set it. Deciding it from an outcome would leave the cell claiming to be
     * working after the work finished.
     */
    @Test
    fun `the in-flight state is never decided from an outcome`() {
        val flags = listOf(false, true)
        val cases = listOf("", "hello").flatMap { input ->
            listOf(null, "", "привет").flatMap { translated ->
                flags.flatMap { modelMissing ->
                    flags.map { failed -> decideTranslatorState(input, translated, modelMissing, failed) }
                }
            }
        }
        cases.forEach { state -> assertNotEquals(TranslatorState.IN_PROGRESS, state) }
    }
}
