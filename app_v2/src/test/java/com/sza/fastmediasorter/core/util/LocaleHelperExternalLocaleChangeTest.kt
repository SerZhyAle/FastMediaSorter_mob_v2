package com.sza.fastmediasorter.core.util

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.os.SystemClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * S3092: a per-app locale set from outside the process - system settings, `cmd locale
 * set-app-locales` - reaches neither [LocaleHelper.saveLanguage] nor [LocaleHelper.resetLanguage],
 * so the in-memory language cache is the only thing that can answer wrongly. The defect is visible
 * only on the SECOND resolution in one process, because the first one is what fills that cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class LocaleHelperExternalLocaleChangeTest {

    private val originalDefault: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        // Both the cache and the process default are global: leaving either set decides a neighbour's result.
        LocaleHelper.resetLanguage(RuntimeEnvironment.getApplication())
        Locale.setDefault(originalDefault)
    }

    @Test
    fun `getLanguage follows a per-app locale replaced outside the app`() {
        val context = RuntimeEnvironment.getApplication()

        setApplicationLocale(context, LANGUAGE_RU)
        assertEquals(LANGUAGE_RU, LocaleHelper.getLanguage(context))

        setApplicationLocale(context, LANGUAGE_UK)

        assertEquals(LANGUAGE_UK, LocaleHelper.getLanguage(context))
    }

    // S4512: the per-emission memo must coalesce the collectors of ONE snapshot and nothing else, or it
    // would undo the S3092 guarantee above for the settings flow.
    @Test
    fun `getLanguageForSnapshot shares one read between callers of the same snapshot`() {
        val context = RuntimeEnvironment.getApplication()
        val snapshot = Any()

        setApplicationLocale(context, LANGUAGE_RU)
        assertEquals(LANGUAGE_RU, LocaleHelper.getLanguageForSnapshot(snapshot, context))

        setApplicationLocale(context, LANGUAGE_UK)

        assertEquals(LANGUAGE_RU, LocaleHelper.getLanguageForSnapshot(snapshot, context))
    }

    @Test
    fun `getLanguageForSnapshot reads again for a new snapshot`() {
        val context = RuntimeEnvironment.getApplication()

        setApplicationLocale(context, LANGUAGE_RU)
        assertEquals(LANGUAGE_RU, LocaleHelper.getLanguageForSnapshot(Any(), context))

        setApplicationLocale(context, LANGUAGE_UK)

        assertEquals(LANGUAGE_UK, LocaleHelper.getLanguageForSnapshot(Any(), context))
    }

    @Test
    fun `getLanguageForSnapshot reads again once the memo has expired`() {
        val context = RuntimeEnvironment.getApplication()
        val snapshot = Any()

        setApplicationLocale(context, LANGUAGE_RU)
        assertEquals(LANGUAGE_RU, LocaleHelper.getLanguageForSnapshot(snapshot, context))

        setApplicationLocale(context, LANGUAGE_UK)
        SystemClock.sleep(MEMO_EXPIRY_MS)

        assertEquals(LANGUAGE_UK, LocaleHelper.getLanguageForSnapshot(snapshot, context))
    }

    @Test
    fun `getLanguageForSnapshot reads again after a language save`() {
        val context = RuntimeEnvironment.getApplication()
        val snapshot = Any()

        setApplicationLocale(context, LANGUAGE_RU)
        assertEquals(LANGUAGE_RU, LocaleHelper.getLanguageForSnapshot(snapshot, context))

        LocaleHelper.saveLanguage(context, LANGUAGE_UK)

        assertEquals(LANGUAGE_UK, LocaleHelper.getLanguageForSnapshot(snapshot, context))
    }

    private fun setApplicationLocale(context: Context, languageTag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(languageTag)
    }

    private companion object {
        const val LANGUAGE_RU = "ru"
        const val LANGUAGE_UK = "uk"

        // Past the memo's TTL with margin; SystemClock.sleep only advances Robolectric's clock.
        const val MEMO_EXPIRY_MS = 5_000L
    }
}
