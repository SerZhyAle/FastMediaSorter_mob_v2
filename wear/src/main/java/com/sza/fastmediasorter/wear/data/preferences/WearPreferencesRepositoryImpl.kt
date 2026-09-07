package com.sza.fastmediasorter.wear.data.preferences

import com.sza.fastmediasorter.wear.data.preferences.sections.WearAppearancePreferencesImpl
import com.sza.fastmediasorter.wear.data.preferences.sections.WearBrowsePreferencesImpl
import com.sza.fastmediasorter.wear.data.preferences.sections.WearMediaTypePreferencesImpl
import com.sza.fastmediasorter.wear.data.preferences.sections.WearMiniAppPreferencesImpl
import com.sza.fastmediasorter.wear.data.preferences.sections.WearPlaybackPreferencesImpl
import com.sza.fastmediasorter.wear.data.preferences.sections.WearSettingsSyncPreferencesImpl
import com.sza.fastmediasorter.wear.data.preferences.sections.WearStreamsPreferencesImpl
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearAppearancePreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearBrowsePreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearMediaTypePreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearMiniAppPreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearPlaybackPreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearSettingsSyncPreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearStreamsPreferences
import javax.inject.Inject

/**
 * DataStore-based implementation of [WearPreferencesRepository].
 *
 * S2655: holds no member of its own - each themed section owns its settings and this class only
 * composes them, so the count detekt's `TooManyFunctions` measures is one section's rather than the
 * whole settings surface of the watch. All seven sections read and write the single `wear_settings`
 * store, handed to them as [WearSettingsDataStore].
 */
class WearPreferencesRepositoryImpl @Inject constructor(
    mediaTypes: WearMediaTypePreferencesImpl,
    playback: WearPlaybackPreferencesImpl,
    browse: WearBrowsePreferencesImpl,
    streams: WearStreamsPreferencesImpl,
    appearance: WearAppearancePreferencesImpl,
    miniApps: WearMiniAppPreferencesImpl,
    settingsSync: WearSettingsSyncPreferencesImpl
) : WearPreferencesRepository,
    WearMediaTypePreferences by mediaTypes,
    WearPlaybackPreferences by playback,
    WearBrowsePreferences by browse,
    WearStreamsPreferences by streams,
    WearAppearancePreferences by appearance,
    WearMiniAppPreferences by miniApps,
    WearSettingsSyncPreferences by settingsSync
