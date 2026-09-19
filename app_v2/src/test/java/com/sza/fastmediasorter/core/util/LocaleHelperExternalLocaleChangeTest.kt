package com.sza.fastmediasorter.core.util

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
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

    private fun setApplicationLocale(context: Context, languageTag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(languageTag)
    }

    private companion object {
        const val LANGUAGE_RU = "ru"
        const val LANGUAGE_UK = "uk"
    }
}
