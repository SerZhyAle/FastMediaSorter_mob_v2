package com.sza.fastmediasorter.wear.domain.model

/**
 * Payload for receiving Wear companion settings pushed from the phone.
 * Fields mirror the setters in WearPreferencesRepository.
 */
data class WearSettingsPayload(
    val audioEnabled: Boolean,
    val videoEnabled: Boolean,
    val imagesEnabled: Boolean,
    val slideshowEnabled: Boolean,
    val slideshowIntervalSeconds: Int,
    val downloadAlbumArt: Boolean,
    // S1781: nullable with a null default - an older phone omits these keys, and only a nullable
    // field lets the applying side leave the watch's own stored value untouched.
    val viewMode: String? = null,
    val keepScreenAwakeOutsidePlayers: Boolean? = null,
    // S1730: the file list keeps its own view, separate from the navigation screens above.
    val fileListViewMode: String? = null,
    // S1814: active interface language of the phone, nullable so older phones do not clear watch locale.
    val appLanguage: String? = null,
    // S2000: name of a WearBackgroundMode. Only the choice rides here - the picture itself goes over
    // the file-transfer channel, because this payload is Gson-encoded and a ByteArray would serialize
    // as an array of numbers, pushing the data item past the size where it is dropped in silence.
    val backgroundMode: String? = null,
    // S2093: the watch row that had no phone control until this ticket.
    val streamsSectionEnabled: Boolean? = null,
    // S2130: the fourth allowed-type switch. Nullable unlike its three siblings above for the S1781
    // reason - a phone that predates it omits it, and only a nullable field lets this side keep its
    // own stored value instead of reading the absence as "documents switched off".
    val documentsEnabled: Boolean? = null,
    // S2209: disable animations toggle synced from/to phone.
    val disableAnimations: Boolean? = null,
    // S2166: whether audio keeps playing after the app is minimized. Nullable for the S1781 reason -
    // a phone that predates it omits the key, and only a nullable field lets this side keep its own
    // stored value instead of reading the absence as "the owner switched background playback off".
    val backgroundPlaybackEnabled: Boolean? = null,
    // S2093: contract field name to epoch-millis of that field's last edit on the sending side. One map
    // rather than a companion field per setting, so a later registry entry needs no new contract field
    // and no new storage key. Absent entirely on a side that predates the two-way exchange, which the
    // merge reads as "apply the incoming value", preserving today's one-way behaviour.
    val fieldTimestamps: Map<String, Long>? = null,
    // S2093: device traits the other side cannot infer, keyed by
    // WearSettingsRegistry.CAPABILITY_AUTO_ROTATION_SENSOR and its future peers.
    val capabilities: Map<String, Boolean>? = null,
    // S2461: the SENDER's own version name, not a setting - it says which build produced this packet, so
    // the receiving side can tell "the settings did not arrive" from "an older build accepted them".
    // Nullable because the pair updates as two builds at different times: a partner that predates this
    // field omits it and is served exactly as before. The envelope's schemaVersion is deliberately not
    // raised (ADR-1) - a hard version check would turn a mismatched pair into a refusal to sync.
    val appVersionName: String? = null,
    // S2505: player panel auto-hide duration in seconds.
    val panelAutoHideSeconds: Int? = null
)
