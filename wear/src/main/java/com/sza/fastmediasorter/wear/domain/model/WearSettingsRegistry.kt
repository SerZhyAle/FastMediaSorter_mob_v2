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
 * @param watchRowAnchor S2169: a literal source token marking this setting's row in its watch
 *   settings screen, so the parity gate can compare the declared order against the drawn order.
 * @param companionRowTag S2169: the `testTag` prefix of this setting's row in the phone companion
 *   window, or null when the window shows no row for it (watch-only settings).
 * @param exceptionReason why it is one-sided, non-null exactly when [ownership] is not
 *   [WearSettingOwnership.BOTH].
 */
data class WearSettingScope(
    val field: String,
    val watchPreferenceKey: String?,
    val docScopeId: String?,
    val valueType: String,
    val ownership: WearSettingOwnership,
    val watchRowAnchor: String? = null,
    val companionRowTag: String? = null,
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
 *
 * S2169: the registry also declares the canonical structure of the watch settings menu - which group
 * a row sits in and in what order, through [menuRowsByGroup] - because order and grouping were the one
 * property no gate could see: the watch menu and the companion window grouped the same settings
 * differently and nothing refused. One anchor per surface ties the declaration to the drawn rows.
 */
object WearSettingsRegistry {

    const val TYPE_BOOLEAN = "Boolean"
    const val TYPE_INT = "Int"
    const val TYPE_ENUM_NAME = "EnumName"
    const val TYPE_LANGUAGE_TAG = "LanguageTag"
    const val TYPE_FILE = "File"

    /** Capability key reported by the watch when its hardware carries a rotation sensor. */
    const val CAPABILITY_AUTO_ROTATION_SENSOR = "autoRotationSensor"

    /**
     * S2169: the watch settings menu's rows per group, in menu order - the single place a row's
     * group and position are declared. The companion window mirrors this sequence; a surface may
     * skip a row the other side owns, never reorder or regroup it.
     */
    val menuRowsByGroup: Map<String, List<String>> = mapOf(
        "MEDIA_TYPES" to listOf(
            "audioEnabled",
            "videoEnabled",
            "imagesEnabled",
            "documentsEnabled",
            "streamsSectionEnabled"
        ),
        "SLIDESHOW" to listOf("slideshowEnabled", "slideshowIntervalSeconds"),
        "SCREEN" to listOf("viewMode", "fileListViewMode", "backgroundMode", "keepScreenAwakeOutsidePlayers"),
        "OTHER" to listOf(
            "downloadAlbumArt",
            "disableAnimations",
            "autoRotationEnabled",
            "backgroundPlaybackEnabled",
            "voiceNoteSendPolicy",
            "panelAutoHideSeconds"
        )
    )

    /** S2169: the canonical group sequence, in the order the watch menu shows them. */
    val menuGroups: List<String> = menuRowsByGroup.keys.toList()

    val entries: List<WearSettingScope> = listOf(
        WearSettingScope(
            field = "audioEnabled",
            watchPreferenceKey = "wear_audio_enabled",
            docScopeId = "wearEnableAudio",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "WearContentType.MUSIC",
            companionRowTag = "wearSwitchAudio"
        ),
        WearSettingScope(
            field = "videoEnabled",
            watchPreferenceKey = "wear_video_enabled",
            docScopeId = "wearEnableVideo",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "WearContentType.VIDEO",
            companionRowTag = "wearSwitchVideo"
        ),
        WearSettingScope(
            field = "imagesEnabled",
            watchPreferenceKey = "wear_images_enabled",
            docScopeId = "wearEnableImages",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "WearContentType.IMAGE",
            companionRowTag = "wearSwitchImages"
        ),
        WearSettingScope(
            field = "documentsEnabled",
            watchPreferenceKey = "wear_documents_enabled",
            docScopeId = "wearEnableDocuments",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "WearContentType.DOCUMENT",
            companionRowTag = "wearSwitchDocuments"
        ),
        WearSettingScope(
            field = "slideshowEnabled",
            watchPreferenceKey = "wear_slideshow_enabled",
            docScopeId = "wearEnableSlideshow",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "toggleSlideshow",
            companionRowTag = "wearSwitchSlideshow"
        ),
        WearSettingScope(
            field = "slideshowIntervalSeconds",
            watchPreferenceKey = "wear_slideshow_interval_seconds",
            docScopeId = "wearSlideshowInterval",
            valueType = TYPE_INT,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "setSlideshowInterval",
            companionRowTag = "wearSlideshowInterval"
        ),
        WearSettingScope(
            field = "downloadAlbumArt",
            watchPreferenceKey = "wear_download_album_art",
            docScopeId = "wearDownloadAlbumArt",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "toggleAlbumArt",
            companionRowTag = "wearSwitchAlbumArt"
        ),
        WearSettingScope(
            field = "viewMode",
            watchPreferenceKey = "wear_view_mode",
            docScopeId = "wearViewMode",
            valueType = TYPE_ENUM_NAME,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "setViewMode",
            companionRowTag = "wearViewMode"
        ),
        WearSettingScope(
            field = "fileListViewMode",
            watchPreferenceKey = "wear_file_list_view_mode",
            docScopeId = "wearFileListViewMode",
            valueType = TYPE_ENUM_NAME,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "setFileListViewMode",
            companionRowTag = "wearFileListViewMode"
        ),
        WearSettingScope(
            field = "keepScreenAwakeOutsidePlayers",
            watchPreferenceKey = "wear_keep_screen_awake",
            docScopeId = "wearKeepScreenAwake",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "toggleKeepScreenAwakeOutsidePlayers",
            companionRowTag = "wearSwitchKeepAwake"
        ),
        WearSettingScope(
            field = "backgroundPlaybackEnabled",
            watchPreferenceKey = "wear_background_playback",
            docScopeId = "wearBackgroundPlayback",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "toggleBackgroundPlayback",
            companionRowTag = "wearSwitchBackgroundPlayback"
        ),
        WearSettingScope(
            field = "backgroundMode",
            watchPreferenceKey = "wear_background_mode",
            docScopeId = "wearBackgroundMode",
            valueType = TYPE_ENUM_NAME,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "setBackgroundMode",
            companionRowTag = "wearBackgroundMode_"
        ),
        WearSettingScope(
            field = "streamsSectionEnabled",
            watchPreferenceKey = "wear_streams_section_enabled",
            docScopeId = "wearStreamsSection",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "toggleStreamsSection",
            companionRowTag = "wearSwitchStreams"
        ),
        WearSettingScope(
            field = "disableAnimations",
            watchPreferenceKey = "wear_disable_animations",
            docScopeId = "wearDisableAnimations",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "toggleDisableAnimations",
            companionRowTag = "wearSwitchDisableAnimations"
        ),
        WearSettingScope(
            field = "autoRotationEnabled",
            watchPreferenceKey = "wear_auto_rotation_enabled",
            docScopeId = "wearAutoRotation",
            valueType = TYPE_BOOLEAN,
            ownership = WearSettingOwnership.WATCH_ONLY,
            watchRowAnchor = "toggleAutoRotation",
            companionRowTag = null,
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
            watchRowAnchor = "setVoiceNoteSendPolicy",
            companionRowTag = null,
            exceptionReason = "ADR-2: decided where the note is recorded, next to the auto-rotation row " +
                "it sits beside on the watch."
        ),
        WearSettingScope(
            field = "panelAutoHideSeconds",
            watchPreferenceKey = "wear_panel_auto_hide_seconds",
            docScopeId = "wearPanelAutoHide",
            valueType = TYPE_INT,
            ownership = WearSettingOwnership.BOTH,
            watchRowAnchor = "setPanelAutoHideSeconds",
            companionRowTag = "wearPanelAutoHide"
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
        // S2169: the menu map and the entry list must name the same settings, or the canonical order
        // and the transfer contract describe different worlds. The two PHONE_ONLY entries carry no
        // watch-menu row by design and are the only entries outside the map.
        val mappedFields = menuRowsByGroup.values.flatten()
        val entryFields = entries.map { it.field }.toSet()
        val unknownMapped = mappedFields.filter { it !in entryFields }
        require(unknownMapped.isEmpty()) {
            "WearSettingsRegistry: menu map names unknown field(s) $unknownMapped"
        }
        val expectedUnmapped = setOf("appLanguage", "backgroundImage")
        val unmapped = entryFields - mappedFields.toSet()
        require(unmapped == expectedUnmapped) {
            "WearSettingsRegistry: entries outside the menu map are $unmapped, expected $expectedUnmapped"
        }
        val anchorless = mappedFields.filter { field -> byField(field)?.watchRowAnchor == null }
        require(anchorless.isEmpty()) {
            "WearSettingsRegistry: menu row(s) without a watchRowAnchor: $anchorless"
        }
        val untagged = mappedFields.filter { field ->
            val scope = byField(field)
            scope != null &&
                scope.ownership == WearSettingOwnership.BOTH &&
                scope.companionRowTag == null
        }
        require(untagged.isEmpty()) {
            "WearSettingsRegistry: shared menu row(s) without a companionRowTag: $untagged"
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
