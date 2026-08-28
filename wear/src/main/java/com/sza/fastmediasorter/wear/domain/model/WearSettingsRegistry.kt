package com.sza.fastmediasorter.wear.domain.model

/**
 * S2093: which side of the pair may change a watch setting.
 */
enum class WearSettingOwnership {
    /** Editable on the watch and in the phone companion window, and merged by the two-way exchange. */
    BOTH,

    /** Editable on the watch only. Requires a recorded [WearSettingScope.exceptionReason]. */
    WATCH_ONLY,

    /** Editable on the phone only. Requires a recorded [WearSettingScope.exceptionReason]. */
    PHONE_ONLY
}

/**
 * S2093: one watch setting, declared once.
 *
 * @param field name of the matching [WearSettingsPayload] field, and the key this setting's edit time
 *   rides under in `WearSettingsPayload.fieldTimestamps`.
 * @param watchPreferenceKey the watch DataStore key backing it, or null when the setting has no watch
 *   storage at all.
 * @param docScopeId the `SettingsDocScopeCatalog.wearEntries` key publishing it, or null when the owner
 *   never sees it as a settings row.
 * @param valueType the value shape carried by the contract field.
 * @param ownership which side may change it.
 * @param exceptionReason why it is one-sided, non-null exactly when [ownership] is not
 *   [WearSettingOwnership.BOTH].
 */
data class WearSettingScope(
    val field: String,
    val watchPreferenceKey: String?,
    val docScopeId: String?,
    val valueType: String,
    val ownership: WearSettingOwnership,
    val exceptionReason: String? = null
)

/**
 * S2093: the single declared list of watch settings, watch-module copy.
 *
 * Mirrored verbatim from `app_v2/../domain/model/WearSettingsRegistry.kt` - the two must not drift.
 * The modules compile separately with no shared artifact, so this is a hand-kept mirror like
 * [WearSettingsPayload] and `WearDataLayerPaths` beside it. `scripts/quality/assert-wear-settings-parity.ps1`
 * is what enforces the mirror and the four consumers derived from it.
 *
 * Four independently maintained lists - the transfer contract, the companion window, the watch settings
 * screens and the published settings reference - are what produced the divergence this ticket removes.
 * A setting added to one of them and not the others fails that gate rather than reaching a release.
 */
object WearSettingsRegistry {

    const val TYPE_BOOLEAN = "Boolean"
    const val TYPE_INT = "Int"
    const val TYPE_ENUM_NAME = "EnumName"
    const val TYPE_LANGUAGE_TAG = "LanguageTag"
    const val TYPE_FILE = "File"

    /** Capability key reported by the watch when its hardware carries a rotation sensor. */
    const val CAPABILITY_AUTO_ROTATION_SENSOR = "autoRotationSensor"

    val entries: List<WearSettingScope> = listOf(
        WearSettingScope(
            field = "audioEnabled",
            watchPreferenceKey = "wear_audio_enabled",
            docScopeId = "wearEnableAudio",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "videoEnabled",
            watchPreferenceKey = "wear_video_enabled",
            docScopeId = "wearEnableVideo",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "imagesEnabled",
            watchPreferenceKey = "wear_images_enabled",
            docScopeId = "wearEnableImages",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "documentsEnabled",
            watchPreferenceKey = "wear_documents_enabled",
            docScopeId = "wearEnableDocuments",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "slideshowEnabled",
            watchPreferenceKey = "wear_slideshow_enabled",
            docScopeId = "wearEnableSlideshow",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "slideshowIntervalSeconds",
            watchPreferenceKey = "wear_slideshow_interval_seconds",
            docScopeId = "wearSlideshowInterval",
            valueType = TYPE_INT,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "downloadAlbumArt",
            watchPreferenceKey = "wear_download_album_art",
            docScopeId = "wearDownloadAlbumArt",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "viewMode",
            watchPreferenceKey = "wear_view_mode",
            docScopeId = "wearViewMode",
            valueType = TYPE_ENUM_NAME,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "fileListViewMode",
            watchPreferenceKey = "wear_file_list_view_mode",
            docScopeId = "wearFileListViewMode",
            valueType = TYPE_ENUM_NAME,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "keepScreenAwakeOutsidePlayers",
            watchPreferenceKey = "wear_keep_screen_awake",
            docScopeId = "wearKeepScreenAwake",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "backgroundMode",
            watchPreferenceKey = "wear_background_mode",
            docScopeId = "wearBackgroundMode",
            valueType = TYPE_ENUM_NAME,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "streamsSectionEnabled",
            watchPreferenceKey = "wear_streams_section_enabled",
            docScopeId = "wearStreamsSection",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "disableAnimations",
            watchPreferenceKey = "wear_disable_animations",
            docScopeId = "wearDisableAnimations",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH
        ),
        WearSettingScope(
            field = "autoRotationEnabled",
            watchPreferenceKey = "wear_auto_rotation_enabled",
            docScopeId = "wearAutoRotation",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.WATCH_ONLY,
            exceptionReason = "ADR-2: describes the behaviour of one physical watch and does not exist " +
                "at all on a unit without a rotation sensor, so a phone switch would offer something " +
                "that may do nothing."
        ),
        WearSettingScope(
            field = "voiceNoteSendPolicy",
            watchPreferenceKey = "wear_voice_note_send_policy",
            docScopeId = "wearVoiceNoteSendPolicy",
            valueType = TYPE_ENUM_NAME,
            ownership = WearSettingOwnership.WATCH_ONLY,
            exceptionReason = "ADR-2: decided where the note is recorded, next to the auto-rotation row " +
                "it sits beside on the watch."
        ),
        WearSettingScope(
            field = "appLanguage",
            watchPreferenceKey = "wear_app_language",
            docScopeId = null,
            valueType = TYPE_LANGUAGE_TAG,
            ownership = WearSettingOwnership.PHONE_ONLY,
            exceptionReason = "Strategic section 2 Non-goals: the language list is a heavy element and " +
                "stays on the phone; S1814 has the watch inherit the phone's active language."
        ),
        WearSettingScope(
            field = "backgroundImage",
            watchPreferenceKey = null,
            docScopeId = null,
            valueType = TYPE_FILE,
            ownership = WearSettingOwnership.PHONE_ONLY,
            exceptionReason = "ADR-3: choosing the picture means opening the gallery and sending a file " +
                "over the transfer channel; only the two-value mode beside it is light enough for the watch."
        )
    )

    init {
        val duplicates = entries.groupBy { it.field }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "WearSettingsRegistry: duplicate field(s) $duplicates"
        }
        // Checked at construction rather than at review: an exception without a recorded reason is
        // indistinguishable from a forgotten setting, which is what produced the current divergence.
        val unexplained = entries
            .filter { it.ownership != WearSettingOwnership.BOTH && it.exceptionReason.isNullOrBlank() }
            .map { it.field }
        require(unexplained.isEmpty()) {
            "WearSettingsRegistry: one-sided entries need an exceptionReason: $unexplained"
        }
        val overExplained = entries
            .filter { it.ownership == WearSettingOwnership.BOTH && it.exceptionReason != null }
            .map { it.field }
        require(overExplained.isEmpty()) {
            "WearSettingsRegistry: BOTH entries carry no exceptionReason: $overExplained"
        }
    }

    /** Fields both sides may change, and so the fields the two-way merge resolves. */
    val sharedFields: List<String> = entries
        .filter { it.ownership == WearSettingOwnership.BOTH }
        .map { it.field }

    /** Fields the watch owns outright; an incoming value for one of these is ignored by the watch. */
    val watchOnlyFields: Set<String> = entries
        .filter { it.ownership == WearSettingOwnership.WATCH_ONLY }
        .map { it.field }
        .toSet()

    /** Fields the phone owns outright; an incoming value for one of these is ignored by the phone. */
    val phoneOnlyFields: Set<String> = entries
        .filter { it.ownership == WearSettingOwnership.PHONE_ONLY }
        .map { it.field }
        .toSet()

    fun byField(field: String): WearSettingScope? = entries.firstOrNull { it.field == field }
}
