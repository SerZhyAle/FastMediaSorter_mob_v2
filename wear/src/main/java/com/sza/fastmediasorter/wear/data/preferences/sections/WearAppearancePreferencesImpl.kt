package com.sza.fastmediasorter.wear.data.preferences.sections

import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceSection
import com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore
import com.sza.fastmediasorter.wear.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearColorScheme
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearAppearancePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearAppearancePreferencesImpl @Inject constructor(
    settings: WearSettingsDataStore
) : WearPreferenceSection(settings), WearAppearancePreferences {

    // S2000: an absent value reads as the branded animation - the one background that needs no
    // delivered file, so a watch that never received a frame still draws something.
    override val backgroundMode: Flow<WearBackgroundMode> = store.data.map { prefs ->
        WearBackgroundMode.fromNameOrDefault(prefs[WearPreferenceKeys.BACKGROUND_MODE])
    }

    override suspend fun setBackgroundMode(mode: WearBackgroundMode) {
        stampedEdit("backgroundMode") { prefs ->
            prefs[WearPreferenceKeys.BACKGROUND_MODE] = mode.name
        }
    }

    // S2522: an absent value reads as the dark scheme, which is what the watch already looked like, so
    // installing this update does not repaint the watch of an owner who never opens the setting.
    override val colorScheme: Flow<WearColorScheme> = store.data.map { prefs ->
        WearColorScheme.fromNameOrDefault(prefs[WearPreferenceKeys.COLOR_SCHEME])
    }

    override suspend fun setColorScheme(scheme: WearColorScheme) {
        stampedEdit("colorScheme") { prefs ->
            prefs[WearPreferenceKeys.COLOR_SCHEME] = scheme.name
        }
    }

    override val isAnimationsDisabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.WEAR_DISABLE_ANIMATIONS] ?: false
    }

    override suspend fun setAnimationsDisabled(disabled: Boolean) {
        stampedEdit("disableAnimations") { prefs ->
            prefs[WearPreferenceKeys.WEAR_DISABLE_ANIMATIONS] = disabled
        }
    }

    override val powerSavingTrigger: Flow<PowerSavingTrigger> = store.data.map { prefs ->
        PowerSavingTrigger.fromNameOrDefault(prefs[WearPreferenceKeys.WEAR_POWER_SAVING_TRIGGER])
    }

    override suspend fun setPowerSavingTrigger(trigger: PowerSavingTrigger) {
        stampedEdit("powerSavingTrigger") { prefs ->
            prefs[WearPreferenceKeys.WEAR_POWER_SAVING_TRIGGER] = trigger.name
        }
    }

    override val keepScreenAwakeOutsidePlayers: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.KEEP_SCREEN_AWAKE] ?: false
    }

    override suspend fun setKeepScreenAwakeOutsidePlayers(enabled: Boolean) {
        stampedEdit("keepScreenAwakeOutsidePlayers") { prefs ->
            prefs[WearPreferenceKeys.KEEP_SCREEN_AWAKE] = enabled
        }
    }

    override val isAutoRotationEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.AUTO_ROTATION_ENABLED] ?: false
    }

    override suspend fun setAutoRotationEnabled(enabled: Boolean) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.AUTO_ROTATION_ENABLED] = enabled
        }
    }

    override val appLanguage: Flow<String?> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.APP_LANGUAGE]
    }

    override suspend fun setAppLanguage(languageCode: String?) {
        writeNullableString(WearPreferenceKeys.APP_LANGUAGE, languageCode)
    }
}
