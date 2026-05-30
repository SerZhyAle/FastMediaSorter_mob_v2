package com.sza.fastmediasorter.domain.ocr

import com.sza.fastmediasorter.domain.model.AppSettings
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S0300 Phase 07: JVM unit tests for [PaddleOcrEngineContributor.supports] - the
 * engine-type gate combined with the case-insensitive Cyrillic source-code membership.
 * The injected engine and settings are mocked.
 */
class PaddleOcrEngineContributorTest {

    private lateinit var engine: OfflineOcrEngine
    private lateinit var contributor: PaddleOcrEngineContributor

    @Before
    fun setUp() {
        engine = mockk()
        contributor = PaddleOcrEngineContributor(engine)
    }

    private fun settings(engineType: String): AppSettings = mockk {
        every { ocrEngineType } returns engineType
    }

    @Test
    fun engineType_isPaddleOcr() {
        assertEquals("PADDLE_OCR", contributor.engineType)
    }

    @Test
    fun engine_exposesInjectedInstance() {
        assertSame(engine, contributor.engine)
    }

    @Test
    fun supports_paddleEngineAndCyrillicCode_true() {
        assertTrue(contributor.supports(settings("PADDLE_OCR"), "ru"))
        assertTrue(contributor.supports(settings("PADDLE_OCR"), "uk"))
        assertTrue(contributor.supports(settings("PADDLE_OCR"), "bel"))
        assertTrue(contributor.supports(settings("PADDLE_OCR"), "auto"))
    }

    @Test
    fun supports_codeMatchedCaseInsensitively_true() {
        assertTrue(contributor.supports(settings("PADDLE_OCR"), "RU"))
        assertTrue(contributor.supports(settings("PADDLE_OCR"), "Uk"))
    }

    @Test
    fun supports_nonPaddleEngine_false() {
        assertFalse(contributor.supports(settings("TESSERACT"), "ru"))
    }

    @Test
    fun supports_nonCyrillicCode_false() {
        assertFalse(contributor.supports(settings("PADDLE_OCR"), "en"))
        assertFalse(contributor.supports(settings("PADDLE_OCR"), "fr"))
    }

    @Test
    fun supports_paddleEngineButUnknownCode_false() {
        assertFalse(contributor.supports(settings("PADDLE_OCR"), "zz"))
    }
}
