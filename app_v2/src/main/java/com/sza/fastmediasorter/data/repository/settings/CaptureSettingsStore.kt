package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of in-app capture settings: camera photo capture (open-for-edit,
 * destination resource) and microphone recording (enabled, ask-filename, destination).
 *
 * Extracted from SettingsRepositoryImpl as one cohesive "capture" responsibility.
 * Public `AppSettings` shape and persisted key strings are unchanged - behaviour-preserving.
 */
object CaptureSettingsStore {

    private val KEY_CAMERA_OPEN_FOR_EDITING = booleanPreferencesKey("camera_open_for_editing")
    private val KEY_CAMERA_COPY_TO_CLIPBOARD = booleanPreferencesKey("camera_copy_to_clipboard")
    private val KEY_CAMERA_GEOTAG_ENABLED = booleanPreferencesKey("camera_geotag_enabled")
    private val KEY_CAMERA_PHOTOS_DESTINATION_RESOURCE_ID = stringPreferencesKey("camera_photos_destination_resource_id")
    private val KEY_MIC_RECORDING_ENABLED = booleanPreferencesKey("mic_recording_enabled")
    private val KEY_MIC_RECORDING_ASK_FILENAME = booleanPreferencesKey("mic_recording_ask_filename")
    private val KEY_MIC_RECORDING_DESTINATION_RESOURCE_ID = stringPreferencesKey("mic_recording_destination_resource_id")
    private val KEY_SCREEN_RECORDING_ENABLED = booleanPreferencesKey("screen_recording_enabled")
    private val KEY_SCREEN_RECORDING_DESTINATION_RESOURCE_ID = stringPreferencesKey("screen_recording_destination_resource_id")
    private val KEY_SCREEN_RECORDING_DISCLOSURE_ACCEPTED = booleanPreferencesKey("screen_recording_disclosure_accepted")

    /** Capture fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val cameraCaptureOpenForEditing: Boolean,
        val cameraCaptureCopyToClipboard: Boolean,
        val cameraGeotagEnabled: Boolean,
        val cameraPhotosDestinationResourceId: String?,
        val micRecordingEnabled: Boolean,
        val micRecordingAskFilename: Boolean,
        val micRecordingDestinationResourceId: String?,
        val screenRecordingEnabled: Boolean,
        val screenRecordingDestinationResourceId: String?,
        val screenRecordingDisclosureAccepted: Boolean,
    )

    fun read(preferences: Preferences): Values = Values(
        cameraCaptureOpenForEditing = preferences[KEY_CAMERA_OPEN_FOR_EDITING] ?: false,
        cameraCaptureCopyToClipboard = preferences[KEY_CAMERA_COPY_TO_CLIPBOARD] ?: false,
        cameraGeotagEnabled = preferences[KEY_CAMERA_GEOTAG_ENABLED] ?: false,
        cameraPhotosDestinationResourceId = preferences[KEY_CAMERA_PHOTOS_DESTINATION_RESOURCE_ID],
        micRecordingEnabled = preferences[KEY_MIC_RECORDING_ENABLED] ?: false,
        micRecordingAskFilename = preferences[KEY_MIC_RECORDING_ASK_FILENAME] ?: true,
        micRecordingDestinationResourceId = preferences[KEY_MIC_RECORDING_DESTINATION_RESOURCE_ID],
        screenRecordingEnabled = preferences[KEY_SCREEN_RECORDING_ENABLED] ?: false,
        screenRecordingDestinationResourceId = preferences[KEY_SCREEN_RECORDING_DESTINATION_RESOURCE_ID],
        screenRecordingDisclosureAccepted = preferences[KEY_SCREEN_RECORDING_DISCLOSURE_ACCEPTED] ?: false,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_CAMERA_OPEN_FOR_EDITING] = settings.cameraCaptureOpenForEditing
        preferences[KEY_CAMERA_COPY_TO_CLIPBOARD] = settings.cameraCaptureCopyToClipboard
        preferences[KEY_CAMERA_GEOTAG_ENABLED] = settings.cameraGeotagEnabled
        preferences.setOrRemove(KEY_CAMERA_PHOTOS_DESTINATION_RESOURCE_ID, settings.cameraPhotosDestinationResourceId)
        preferences[KEY_MIC_RECORDING_ENABLED] = settings.micRecordingEnabled
        preferences[KEY_MIC_RECORDING_ASK_FILENAME] = settings.micRecordingAskFilename
        preferences.setOrRemove(KEY_MIC_RECORDING_DESTINATION_RESOURCE_ID, settings.micRecordingDestinationResourceId)
        preferences[KEY_SCREEN_RECORDING_ENABLED] = settings.screenRecordingEnabled
        preferences.setOrRemove(KEY_SCREEN_RECORDING_DESTINATION_RESOURCE_ID, settings.screenRecordingDestinationResourceId)
        preferences[KEY_SCREEN_RECORDING_DISCLOSURE_ACCEPTED] = settings.screenRecordingDisclosureAccepted
    }
}
