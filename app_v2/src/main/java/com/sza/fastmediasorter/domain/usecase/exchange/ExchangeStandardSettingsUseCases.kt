package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeSettingsClient
import com.sza.fastmediasorter.data.remote.exchange.ExchangeSettingsGetResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeSettingsSaveResult
import com.sza.fastmediasorter.domain.exchange.StandardSettingsSerializer
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Saves current local application settings to Media Exchange Server as standard settings profile.
 */
class SaveStandardSettingsUseCase @Inject constructor(
    private val client: DeviceExchangeSettingsClient,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        entry: DeviceExchangeAccountEntry,
        selectedGroups: Set<String> = StandardSettingsSerializer.ALL_GROUPS
    ): ExchangeSettingsSaveResult {
        val settings = settingsRepository.getSettings().first()
        val payloadJson = StandardSettingsSerializer.serialize(settings, selectedGroups)
        return client.saveStandard(entry, payloadJson)
    }
}

/**
 * Retrieves standard settings profile from Media Exchange Server and applies selected groups locally.
 */
class ApplyStandardSettingsUseCase @Inject constructor(
    private val client: DeviceExchangeSettingsClient,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        entry: DeviceExchangeAccountEntry,
        selectedGroups: Set<String> = StandardSettingsSerializer.ALL_GROUPS
    ): ExchangeSettingsGetResult {
        val result = client.getStandard(entry)
        if (result is ExchangeSettingsGetResult.Success) {
            settingsRepository.updateSettings { current ->
                StandardSettingsSerializer.deserializeAndApply(current, result.settingsPayload, selectedGroups)
            }
        }
        return result
    }
}
