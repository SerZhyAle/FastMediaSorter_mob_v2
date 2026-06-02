package com.sza.fastmediasorter.data.model

/**
 * Device profile - user's intent for device usage mode.
 * Enumeration of 11 selectable device profiles + Other fallback.
 *
 * When you ADD a profile (S0327): add a column to `app_v2/src/main/assets/device_profile_presets.csv`
 * (run `scripts/check_device_profile_presets.ps1 -AddMissing`), map it in
 * `DeviceProfilePresetCsvDataSource.COLUMN_TO_PROFILE`, and add its icon/title/description in
 * `DeviceProfileUi` (+ `welcome_profile_*` strings in EN/RU/UK). See `dev/DEVICE_PROFILE_PRESET_MATRIX.md`.
 */
enum class DeviceProfileType {
    PERSONAL_SMARTPHONE,
    HOME_TABLET,
    TV_MEDIA_BOX,
    CAR_HEAD_UNIT,
    MEDIA_PLAYER,
    PHOTO_FRAME,
    VIDEO_PLAYER,
    AUDIO_PLAYER,
    EBOOK_READER,
    VR_HEADSET,
    OTHER
}

/**
 * Source indicating how the profile was selected or determined.
 */
enum class DeviceProfileSource {
    MANUAL_SELECTION,      // User explicitly chose in Welcome or Settings
    AUTO_DETECTED,         // Detector recommended and user accepted
    AUTO_SKIPPED,          // User pressed Skip on Welcome; auto-profile applied
    MIGRATION_EXISTING     // Migrated from pre-profile install; no preset applied
}

/**
 * Confidence level for auto-detected profile.
 * Used to determine whether to show recommendation or fall back to safe default.
 */
enum class DetectionConfidence {
    HIGH,   // Clear Android signals (XR features, automotive, TV/leanback)
    MEDIUM, // Screen size or capability hints (width, telephony)
    LOW,    // Ambiguous or conflicting signals
    NONE    // No detection performed (migration, manual choice)
}

/**
 * Device profile state - holds selected profile, source, confidence, and metadata.
 * Persisted to Room database.
 */
data class DeviceProfile(
    val type: DeviceProfileType,
    val source: DeviceProfileSource,
    val confidence: DetectionConfidence,
    val presetVersion: Int,           // Version of preset matrix applied (if any)
    val appliedAtInstallTime: Boolean, // True if preset was applied on fresh install or Settings change
    val lastModified: Long             // Unix milliseconds UTC
)

/**
 * Detector signal record - tracks which Android signals fired during detection.
 * Used for audit/diagnostics; not persisted.
 */
data class DetectorSignal(
    val signalName: String,            // e.g., "has_xr_feature", "smallest_width_600dp"
    val profileType: DeviceProfileType,
    val confidence: DetectionConfidence
)
