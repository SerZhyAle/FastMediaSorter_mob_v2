package com.sza.fastmediasorter.core.util

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1190: asks Play to deliver a language on demand.
 *
 * An app installed from Play carries only the locales the device asked for at install time, so
 * switching to a language the user never had means fetching it first. A build installed from
 * anywhere else already carries every locale, and the Play library answers "already installed"
 * there - which is why this needs no flavor guard and no build-time capability check.
 *
 * Failure is an ordinary outcome here, not an exception to swallow: the caller keeps the previous
 * language and tells the user why, so every path returns an [Outcome] instead of throwing.
 */
@Singleton
class LanguageSplitInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** What happened to the request. [Failed.reason] is technical - for the log, not for the user. */
    sealed interface Outcome {
        /** The language was already on the device; nothing was downloaded. */
        data object AlreadyInstalled : Outcome

        /** The language was fetched during this call. */
        data object Installed : Outcome

        /** The language is not available; the caller keeps the language it had. */
        data class Failed(val reason: String) : Outcome
    }

    /**
     * Makes [languageTag] available, downloading it if Play has to.
     *
     * Suspends until the install finishes or fails - the caller owns the scope, so a screen that
     * goes away takes the wait with it.
     */
    suspend fun ensureLanguage(languageTag: String): Outcome {
        val locale = Locale.forLanguageTag(languageTag)
        if (locale.language.isEmpty()) {
            Timber.i("LanguageSplitInstaller: '%s' is not a language tag", languageTag)
            return Outcome.Failed("malformed language tag: $languageTag")
        }
        return install(locale)
    }

    private suspend fun install(locale: Locale): Outcome {
        val manager = SplitInstallManagerFactory.create(context)
        val installed = try {
            manager.installedLanguages
        } catch (expected: Exception) {
            expected.rethrowIfCancellation()
            // The service is absent on a build sideloaded outside Play, and on such a build every
            // locale is already packaged - treating that as "present" is the truthful answer.
            Timber.i(
                expected,
                "LanguageSplitInstaller: split service unavailable, treating %s as present",
                locale.language
            )
            setOf(locale.language)
        }
        if (installed.any { it.equals(locale.language, ignoreCase = true) }) {
            return Outcome.AlreadyInstalled
        }
        val request = SplitInstallRequest.newBuilder()
            .addLanguage(locale)
            .build()
        return try {
            manager.startInstall(request).await()
            Timber.i("LanguageSplitInstaller: installed language %s", locale.language)
            Outcome.Installed
        } catch (expected: Exception) {
            expected.rethrowIfCancellation()
            val reason = expected.message ?: expected::class.java.simpleName
            Timber.i(expected, "LanguageSplitInstaller: could not install %s - %s", locale.language, reason)
            Outcome.Failed(reason)
        }
    }
}
