package com.sza.fastmediasorter.ui.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1566: URL assembly is the only pure logic in the search launch layer, and an encoding slip sends a
 * quietly wrong query rather than failing, which no device test catches reliably.
 *
 * Plain JUnit on purpose: the module sets `isReturnDefaultValues`, so an `android.net.Uri`-based encoder
 * would return null here and the assertions would hold for the wrong reason.
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. LauncherStarterSetsParityTest)
class WebSearchLaunchManagerTest {

    @Test
    fun `a single word is appended verbatim`() {
        assertEquals(
            "https://www.google.com/search?q=kotlin",
            WebSearchLaunchManager.buildSearchUrl("kotlin"),
        )
    }

    @Test
    fun `spaces between words are encoded`() {
        assertEquals(
            "https://www.google.com/search?q=hello+world",
            WebSearchLaunchManager.buildSearchUrl("hello world"),
        )
    }

    @Test
    fun `query separators and non-latin characters are percent-encoded`() {
        assertEquals(
            "https://www.google.com/search?q=a%26b%3Fc+%D0%BA%D0%BE%D1%82",
            WebSearchLaunchManager.buildSearchUrl("a&b?c кот"),
        )
    }
}
