package com.sza.fastmediasorter.wear.data.preferences.sections

import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.wear.data.preferences.SettingTimestampsCodec
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceSection
import com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearSettingsSyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearSettingsSyncPreferencesImpl @Inject constructor(
    settings: WearSettingsDataStore
) : WearPreferenceSection(settings), WearSettingsSyncPreferences {

    // S1862: stored by name, like every other enum preference - an ordinal would re-point stored
    // values the day a third policy is inserted between the two.
    override val voiceNoteSendPolicy: Flow<VoiceNoteSendPolicy> = store.data.map { prefs ->
        VoiceNoteSendPolicy.fromNameOrDefault(prefs[WearPreferenceKeys.VOICE_NOTE_SEND_POLICY])
    }

    override suspend fun setVoiceNoteSendPolicy(policy: VoiceNoteSendPolicy) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.VOICE_NOTE_SEND_POLICY] = policy.name
        }
    }

    // S1961: absent reads as "not asked yet", which is what an untouched watch is.
    override val notificationPermissionAsked: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.NOTIFICATION_PERMISSION_ASKED] ?: false
    }

    override suspend fun setNotificationPermissionAsked(asked: Boolean) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.NOTIFICATION_PERMISSION_ASKED] = asked
        }
    }

    // S3186: absent reads as "not finished", which is what a fresh install is.
    override val onboardingCompleted: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.ONBOARDING_COMPLETED] ?: false
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    override val settingTimestamps: Flow<Map<String, Long>> = store.data.map { prefs ->
        SettingTimestampsCodec.decode(prefs[WearPreferenceKeys.SETTING_TIMESTAMPS])
    }

    override suspend fun stampSetting(field: String, atEpochMillis: Long) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.SETTING_TIMESTAMPS] = SettingTimestampsCodec.encode(
                SettingTimestampsCodec.decode(prefs[WearPreferenceKeys.SETTING_TIMESTAMPS]) +
                    (field to atEpochMillis)
            )
        }
    }

    override val lastSettingsSyncAt: Flow<Long> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.LAST_SETTINGS_SYNC] ?: 0L
    }

    override suspend fun markSettingsSynced(atEpochMillis: Long) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.LAST_SETTINGS_SYNC] = atEpochMillis
        }
    }
}
