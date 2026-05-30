package com.sza.fastmediasorter.domain.ocr

import android.graphics.Bitmap
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [OfflineOcrEngineProvider] - engine selection, default fallback, and release dedup.
 * The [Bitmap] argument is an opaque mock that is only forwarded to the (mocked) engines.
 */
class OfflineOcrEngineProviderTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val bitmap = mockk<Bitmap>()
    private val settings = AppSettings()

    private fun engine() = mockk<OfflineOcrEngine>(relaxed = true)

    private fun contributor(engine: OfflineOcrEngine, supports: Boolean) =
        mockk<OcrEngineContributor> {
            every { this@mockk.engine } returns engine
            every { this@mockk.supports(any(), any()) } returns supports
        }

    @Test
    fun `engineFor returns the first supporting contributor engine`() {
        val contributedEngine = engine()
        val default = engine()
        val provider = OfflineOcrEngineProvider(default, setOf(contributor(contributedEngine, supports = true)))

        assertSame(contributedEngine, provider.engineFor(settings, "rus"))
    }

    @Test
    fun `engineFor falls back to the default when no contributor supports the input`() {
        val default = engine()
        val provider = OfflineOcrEngineProvider(default, setOf(contributor(engine(), supports = false)))

        assertSame(default, provider.engineFor(settings, "eng"))
    }

    @Test
    fun `recognizeTextWithFallback returns the preferred engine result when non-blank`() = runTest {
        val default = engine()
        val preferred = engine()
        coEvery { preferred.recognizeText(any(), any()) } returns "hello"
        val provider = OfflineOcrEngineProvider(default, emptySet())

        val result = provider.recognizeTextWithFallback(settings, bitmap, "rus", "eng", preferred)

        assertEquals("hello", result)
    }

    @Test
    fun `recognizeTextWithFallback retries on the default engine when preferred returns blank`() = runTest {
        val default = engine()
        val preferred = engine()
        coEvery { preferred.recognizeText(any(), any()) } returns ""
        coEvery { default.recognizeText(any(), any()) } returns "from-default"
        val provider = OfflineOcrEngineProvider(default, emptySet())

        val result = provider.recognizeTextWithFallback(settings, bitmap, "rus", "eng", preferred)

        assertEquals("from-default", result)
    }

    @Test
    fun `recognizeTextWithFallback does not double-fallback when preferred is already the default`() = runTest {
        val default = engine()
        coEvery { default.recognizeText(any(), any()) } returns null
        val provider = OfflineOcrEngineProvider(default, emptySet())

        val result = provider.recognizeTextWithFallback(settings, bitmap, "rus", "eng", default)

        assertNull(result)
    }

    @Test
    fun `recognizeTextBlocksWithFallback falls back on an empty block list`() = runTest {
        val default = engine()
        val preferred = engine()
        val blocks = listOf<OcrTextBlock>(mockk())
        coEvery { preferred.recognizeTextBlocks(any(), any()) } returns emptyList()
        coEvery { default.recognizeTextBlocks(any(), any()) } returns blocks
        val provider = OfflineOcrEngineProvider(default, emptySet())

        val result = provider.recognizeTextBlocksWithFallback(settings, bitmap, "rus", "eng", preferred)

        assertEquals(blocks, result)
    }

    @Test
    fun `release releases the default and every distinct contributor engine once`() {
        val default = engine()
        val sharedEngine = engine()
        every { default.release() } just Runs
        every { sharedEngine.release() } just Runs
        // Two contributors reference the same engine instance - it must be released only once.
        val provider = OfflineOcrEngineProvider(
            default,
            setOf(contributor(sharedEngine, supports = false), contributor(sharedEngine, supports = false)),
        )

        provider.release()

        verify(exactly = 1) { default.release() }
        verify(exactly = 1) { sharedEngine.release() }
    }
}
