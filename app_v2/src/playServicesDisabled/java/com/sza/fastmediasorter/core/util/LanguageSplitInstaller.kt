package com.sza.fastmediasorter.core.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0403: the Play-free half of the language-delivery seam.
 *
 * The Play Core counterpart in `src/playServicesEnabled` fetches a locale the store left out of the
 * install. A build distributed as a single APK - which is the only shape F-Droid accepts - ships
 * every locale already, so there is nothing to fetch and [Outcome.AlreadyInstalled] is the truthful
 * answer rather than a stub. Same FQCN and same contract as the enabled copy, so no call site
 * branches on the flavor (Rule 14).
 */
@Singleton
class LanguageSplitInstaller @Inject constructor() {

    /** What happened to the request. [Failed.reason] is technical - for the log, not for the user. */
    sealed interface Outcome {
        /** The language was already on the device; nothing was downloaded. */
        data object AlreadyInstalled : Outcome

        /** The language was fetched during this call. */
        data object Installed : Outcome

        /** The language is not available; the caller keeps the language it had. */
        data class Failed(val reason: String) : Outcome
    }

    /** Every locale is packaged in this build, so the language is available by construction. */
    @Suppress("RedundantSuspendModifier", "UnusedParameter")
    suspend fun ensureLanguage(languageTag: String): Outcome = Outcome.AlreadyInstalled
}
