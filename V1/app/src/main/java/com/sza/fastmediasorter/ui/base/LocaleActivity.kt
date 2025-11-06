package com.sza.fastmediasorter.ui.base

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.utils.PreferenceManager
import java.util.Locale

/**
 * Base activity that applies language settings to all activities
 */
open class LocaleActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val preferenceManager = PreferenceManager(context)
        val languageCode = preferenceManager.getLanguage()

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)

        return context.createConfigurationContext(configuration)
    }
}
