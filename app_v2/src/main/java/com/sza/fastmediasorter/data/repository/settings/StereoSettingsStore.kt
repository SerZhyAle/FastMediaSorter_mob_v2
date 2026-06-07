package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.StereoMode

/**
 * Owns persistence of the global 3D/VR stereo-detection defaults: the DataStore
 * keys, their default values, and the read/write mapping.
 *
 * Extracted from SettingsRepositoryImpl so the stereo domain is a single, named
 * responsibility instead of seven keys inlined among ~160 unrelated ones. The
 * public `AppSettings` shape and every persisted key string are unchanged, so
 * this is behaviour-preserving and invisible to callers - it only moves the
 * "where do stereo keys/defaults live" responsibility out of the god-repository.
 */
object StereoSettingsStore {

    private val KEY_AUTO_DETECT_ENABLED = booleanPreferencesKey("stereo_auto_detect_enabled")
    private val KEY_TRUST_FILENAME = booleanPreferencesKey("stereo_trust_filename")
    private val KEY_TRUST_METADATA = booleanPreferencesKey("stereo_trust_metadata")
    private val KEY_TRUST_ASPECT_RATIO = booleanPreferencesKey("stereo_trust_aspect_ratio")
    private val KEY_AMBIGUITY_BEST_GUESS = booleanPreferencesKey("stereo_ambiguity_best_guess")
    private val KEY_DEFAULT_LAYOUT = stringPreferencesKey("stereo_default_layout")
    private val KEY_DEFAULT_PROJECTION = stringPreferencesKey("stereo_default_projection")

    /** Stereo fields read from DataStore, ready to plug into [AppSettings]. */
    data class Values(
        val autoDetectEnabled: Boolean,
        val trustFilename: Boolean,
        val trustMetadata: Boolean,
        val trustAspectRatio: Boolean,
        val ambiguityBestGuess: Boolean,
        val defaultLayout: StereoMode,
        val defaultProjection: StereoMode,
    )

    fun read(preferences: Preferences): Values = Values(
        autoDetectEnabled = preferences[KEY_AUTO_DETECT_ENABLED] ?: true,
        trustFilename = preferences[KEY_TRUST_FILENAME] ?: true,
        trustMetadata = preferences[KEY_TRUST_METADATA] ?: true,
        trustAspectRatio = preferences[KEY_TRUST_ASPECT_RATIO] ?: false,
        ambiguityBestGuess = preferences[KEY_AMBIGUITY_BEST_GUESS] ?: false,
        // S0326: global 3D/VR default settings. Layout/projection fall back to MONO (plain 2D).
        defaultLayout = preferences[KEY_DEFAULT_LAYOUT]
            ?.let { StereoMode.fromKey(it) }
            ?.takeIf { it != StereoMode.AUTO && it != StereoMode.UNKNOWN }
            ?: StereoMode.MONO,
        defaultProjection = preferences[KEY_DEFAULT_PROJECTION]
            ?.let { StereoMode.fromKey(it) }
            ?.takeIf { it != StereoMode.AUTO && it != StereoMode.UNKNOWN }
            ?: StereoMode.MONO,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_AUTO_DETECT_ENABLED] = settings.stereoAutoDetectEnabled
        preferences[KEY_TRUST_FILENAME] = settings.stereoTrustFilename
        preferences[KEY_TRUST_METADATA] = settings.stereoTrustMetadata
        preferences[KEY_TRUST_ASPECT_RATIO] = settings.stereoTrustAspectRatio
        preferences[KEY_AMBIGUITY_BEST_GUESS] = settings.stereoAmbiguityBestGuess
        preferences[KEY_DEFAULT_LAYOUT] = settings.stereoDefaultLayout.name
        preferences[KEY_DEFAULT_PROJECTION] = settings.stereoDefaultProjection.name
    }
}
