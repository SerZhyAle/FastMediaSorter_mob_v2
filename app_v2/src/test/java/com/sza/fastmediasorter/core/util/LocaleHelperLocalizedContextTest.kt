package com.sza.fastmediasorter.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * S2571: reading a label in another language must not change the language of the whole process.
 * The two cases assert the split from both sides, because a resolver that silently kept mutating the
 * global default is exactly the defect this ticket removed - and only the negative case can catch it
 * coming back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class LocaleHelperLocalizedContextTest {

    private lateinit var originalDefault: Locale

    @Before
    fun setUp() {
        originalDefault = Locale.getDefault()
    }

    @After
    fun tearDown() {
        // The default is process-global: leaving a foreign one behind would decide a neighbour's result.
        Locale.setDefault(originalDefault)
        LocaleHelper.resetLanguage(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `localizedContext carries the requested language and leaves the default alone`() {
        val context = RuntimeEnvironment.getApplication()
        val defaultBefore = Locale.getDefault()

        val localized = LocaleHelper.localizedContext(context, LANGUAGE_UK)

        assertEquals(LANGUAGE_UK, localized.resources.configuration.locales[0].language)
        assertEquals(defaultBefore, Locale.getDefault())
        assertNotEquals(LANGUAGE_UK, Locale.getDefault().language)
    }

    @Test
    fun `applyLocale does change the process default`() {
        val context = RuntimeEnvironment.getApplication()

        val applied = LocaleHelper.applyLocale(context, LANGUAGE_UK)

        assertEquals(LANGUAGE_UK, applied.resources.configuration.locales[0].language)
        assertEquals(LANGUAGE_UK, Locale.getDefault().language)
    }

    /**
     * S2598: the process default answers plausibly for the language and lies about the region, so nothing
     * that asserts the language can catch a region read from it. Own `@Config`: the class runs under a
     * region-less "en", which is the one qualifier under which the two values are indistinguishable.
     *
     * This pins the contract, not the mechanism - which source actually survives a per-app locale is a
     * device question, and the answer measured there is in `systemRegion`'s own KDoc.
     */
    @Test
    @Config(sdk = [34], qualifiers = "en-rUS")
    fun `systemRegion survives a change of interface language`() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(REGION_US, LocaleHelper.systemRegion(context))
        LocaleHelper.applyLocale(context, LANGUAGE_UK)

        assertEquals(LANGUAGE_UK, Locale.getDefault().language)
        assertEquals(REGION_US, LocaleHelper.systemRegion(context))
    }

    private companion object {
        const val LANGUAGE_UK = "uk"
        const val REGION_US = "US"
    }
}
