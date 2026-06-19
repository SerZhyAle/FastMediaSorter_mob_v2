package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StatisticsRepository
import javax.inject.Inject
import timber.log.Timber

/**
 * Toggles the opt-in local usage statistics flag (S0473).
 *
 * Turning collection OFF additionally wipes detailed activity so nothing accrued under consent
 * survives; the always-on baseline (launch count, install version) is intentionally kept
 * (strategic §3.2 "Поведение при выключении", §11.5, ADR-2).
 */
class SetStatisticsCollectionEnabledUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val statistics: StatisticsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settings.setStatisticsEnabled(enabled)
        if (!enabled) {
            statistics.wipeDetailed()
        }
    }
}
