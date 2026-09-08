package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.ui.streams.helpers.StreamCollectionNameResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * S2669: the fall-through that keeps an opaque catalog slug off the screen.
 *
 * Robolectric supplies a real `org.json`: this module sets `isReturnDefaultValues = true`, so on the
 * plain JVM runner every JSONObject call would answer null and these assertions would pass for the
 * wrong reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class StreamCollectionNameResolverTest {

    private val names = """{"en":"Russian TV","ru":"Русское ТВ","ru-RU":"Русское ТВ (РФ)"}"""

    private fun resolve(json: String, locale: Locale) =
        StreamCollectionNameResolver.resolve("ru-tv", json, locale)

    @Test
    fun `the full locale tag wins over the language`() {
        assertEquals("Русское ТВ (РФ)", resolve(names, Locale.forLanguageTag("ru-RU")))
    }

    @Test
    fun `the language-only tag is used when the full tag is absent`() {
        assertEquals("Русское ТВ", resolve(names, Locale.forLanguageTag("ru-UA")))
    }

    @Test
    fun `an unknown locale falls back to en`() {
        assertEquals("Russian TV", resolve(names, Locale.forLanguageTag("ja-JP")))
    }

    @Test
    fun `a blank name is skipped rather than shown`() {
        val blankRu = """{"en":"Russian TV","ru":"   "}"""
        assertEquals("Russian TV", resolve(blankRu, Locale.forLanguageTag("ru-RU")))
    }

    @Test
    fun `a malformed payload falls back to the collection id`() {
        assertEquals("ru-tv", resolve("not json at all", Locale.ENGLISH))
    }

    @Test
    fun `an empty map falls back to the collection id`() {
        assertEquals("ru-tv", resolve("{}", Locale.ENGLISH))
    }
}
