package com.sza.fastmediasorter.data.migration

import android.content.Context
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0386 Phase 13 - run-once upgrade reconciliation (strategic ADR-2, variant 1.1).
 *
 * When a user updates from a build that shipped OCR/translation code in the base to the de-bundle
 * build, their saved toggle stays ON but the native payload has left the base and is not downloaded.
 * That leaves a dishonest "ON but not installed" state. This migration flips such toggles OFF so the
 * state is truthful; re-enabling then runs the normal size-prompt download flow (Phase 06).
 *
 * Only flips a toggle that is ON **and** whose set is not installed - a set still bundled in the
 * flavor (e.g. ML Kit Translate on sideload/VR) reports installed and is left untouched. Idempotent:
 * a sentinel in `SharedPreferences` prevents re-execution, so a user who later re-enables + downloads
 * is never reset again.
 *
 * Not variant 1.2 (auto-download): we never download on upgrade without explicit consent (§2.3-2.5).
 */
@Singleton
class S0386UpgradeReconciliation @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val capabilityRepository: DeliverableCapabilityRepository
) {

    /** Idempotent. Safe to call from `Application.onCreate` every launch - early-exits once done. */
    suspend fun runIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        try {
            val settings = settingsRepository.getSettings().first()

            val ocrNeedsOff = settings.enableOcr &&
                !capabilityRepository.isInstalledBlocking(DeliverableSet.OCR_ENGINES)
            val translationNeedsOff = settings.enableTranslation &&
                !capabilityRepository.isInstalledBlocking(DeliverableSet.TRANSLATION)

            if (ocrNeedsOff || translationNeedsOff) {
                settingsRepository.updateSettings(
                    settings.copy(
                        enableOcr = if (ocrNeedsOff) false else settings.enableOcr,
                        enableTranslation = if (translationNeedsOff) false else settings.enableTranslation
                    )
                )
                Timber.i(
                    "Upgrade reconciliation: forced OFF (ocr=%b, translation=%b) - set not installed after de-bundle",
                    ocrNeedsOff, translationNeedsOff
                )
            }

            // Persist sentinel LAST so a partial-failure run retries on next launch.
            prefs.edit().putBoolean(KEY_DONE, true).commit()
        } catch (t: Throwable) {
            Timber.e(t, "Upgrade reconciliation failed; will retry on next launch")
        }
    }

    private companion object {
        const val PREFS = "s0386_migration"
        const val KEY_DONE = "ondemand_reconcile_done"
    }
}
