package com.sza.fastmediasorter

import android.app.Application
import android.content.res.Configuration
import com.sza.fastmediasorter.utils.CrashHandler
import com.sza.fastmediasorter.utils.Logger
import com.sza.fastmediasorter.utils.PreferenceManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.Locale

class FastMediaSorterApplication : Application() {
    companion object {
        // Timestamp when application started (session start time)
        val sessionStartTime: Long = System.currentTimeMillis()
    }

    override fun onCreate() {
        super.onCreate()

        CrashHandler.init(this)

        Logger.d("Application", "Session started at: $sessionStartTime")

        // Apply saved language
        applySavedLanguage()

        // Register BouncyCastle provider for jCIFS-ng MD4 algorithm support
        try {
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            Logger.d("Application", "BouncyCastle provider registered successfully")
        } catch (e: Exception) {
            Logger.e("Application", "Failed to register BouncyCastle provider", e)
        }
    }

    private fun applySavedLanguage() {
        try {
            val preferenceManager = PreferenceManager(this)
            val savedLanguage = preferenceManager.getLanguage()

            val locale = Locale(savedLanguage)
            Locale.setDefault(locale)

            val configuration = Configuration(resources.configuration)
            configuration.setLocale(locale)

            // Update configuration for app context
            resources.updateConfiguration(configuration, resources.displayMetrics)

            Logger.d("Application", "Applied language: $savedLanguage")
        } catch (e: Exception) {
            Logger.e("Application", "Failed to apply saved language", e)
        }
    }
}
