package com.sza.fastmediasorter.core.util

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
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
        val outcome = try {
            awaitInstall(manager, request, locale.language)
        } catch (expected: Exception) {
            expected.rethrowIfCancellation()
            Outcome.Failed(expected.message ?: expected::class.java.simpleName)
        }
        Timber.i("LanguageSplitInstaller: %s -> %s", locale.language, outcome)
        return outcome
    }

    /**
     * The task [SplitInstallManager.startInstall] returns completes as soon as Play ACCEPTS the
     * request, long before the split is on disk; only the session state stream says when it is.
     * The listener is registered before the request so no state update can slip past it.
     */
    private suspend fun awaitInstall(
        manager: SplitInstallManager,
        request: SplitInstallRequest,
        language: String
    ): Outcome {
        val terminal = CompletableDeferred<Outcome>()
        val listener = SplitInstallStateUpdatedListener { state ->
            if (state.languages().any { it.equals(language, ignoreCase = true) }) {
                terminalOutcome(state)?.let { terminal.complete(it) }
            }
        }
        manager.registerListener(listener)
        try {
            // Session id 0 means Play had nothing to fetch, so no state update will ever arrive.
            if (manager.startInstall(request).await() == 0) terminal.complete(Outcome.Installed)
            return terminal.await()
        } finally {
            manager.unregisterListener(listener)
        }
    }

    private fun terminalOutcome(state: SplitInstallSessionState): Outcome? = when (state.status()) {
        SplitInstallSessionStatus.INSTALLED -> Outcome.Installed
        SplitInstallSessionStatus.FAILED -> Outcome.Failed("install failed, error code ${state.errorCode()}")
        SplitInstallSessionStatus.CANCELED -> Outcome.Failed("install canceled")
        // The confirmation dialog needs an Activity this application-scoped installer does not
        // have; declining keeps the previous language, which the caller already explains.
        SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> Outcome.Failed("download needs user confirmation")
        else -> null
    }
}
