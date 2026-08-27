package com.sza.fastmediasorter.service

/**
 * Path constants for the Wear OS Data Layer.
 * Existing /fms/network_sources/ paths are defined locally in their respective services
 * for backward compatibility and are not listed here.
 */
object WearDataLayerPaths {

    /** Data Item, phone → watch. Carries watch companion settings payload. */
    const val SETTINGS_PUSH = "/fms/wear/settings"

    /** Message, watch → phone. Carries network sources export payload. */
    const val SOURCES_EXPORT = "/fms/watch/sources_export"

    /**
     * Data Item, watch → phone. Carries the watch's own settings payload back to the phone (S2093).
     *
     * A Data Item rather than a Message, matching [PLAYBACK_STATE]: the watch's settings are state, so
     * the phone must be able to read the latest one after reconnecting rather than only catching it live.
     *
     * The `/fms/watch` prefix is the reason no manifest edit is needed. `src/wearGms/AndroidManifest.xml`
     * already declares `pathPrefix="/fms/watch"` for PhoneWearListenerService, added by S1697 after the
     * service handled watch paths in code while the filter matched one unrelated path and GMS silently
     * dropped the rest. A path named outside the prefix would need its own filter entry.
     */
    const val SETTINGS_REPORT = "/fms/watch/settings_report"

    /** Data Item, watch → phone. Carries current playback state. */
    const val PLAYBACK_STATE = "/fms/watch/playback_state"

    /** Message, phone → watch. Carries remote playback command. */
    const val PLAYBACK_CMD = "/fms/phone/playback_cmd"

    /** Message, watch → phone. Carries favorites delta payload. */
    const val FAVORITES_DELTA = "/fms/watch/favorites_delta"

    /** Message, watch → phone. Requests a paired-phone resource page. */
    const val PHONE_RESOURCE_BROWSE_REQUEST = "/fms/watch/phone_resource/browse"

    /** Message, watch → phone. Requests an on-demand paired-phone media channel. */
    const val PHONE_RESOURCE_OPEN_REQUEST = "/fms/watch/phone_resource/open"

    /** Data Item, phone → watch. Carries a correlated paired-phone resource page. */
    const val PHONE_RESOURCE_PAGE = "/fms/phone/phone_resource/page"

    /** Channel, phone → watch. Carries the bytes of one approved paired-phone media item. */
    const val PHONE_RESOURCE_TRANSFER = "/fms/phone/phone_resource/transfer"

    /** Message, watch → phone. Carries one log report for the developer. */
    const val LOG_REPORT_REQUEST = "/fms/watch/log_report"

    /** Message, phone → watch. Answers one log report - accepted, or refused with a reason. */
    const val LOG_REPORT_ACK = "/fms/phone/log_report_ack"

    /** Message, phone → watch. Carries one stream channel description to store on the watch. */
    const val STREAM_TRANSFER = "/fms/phone/stream_transfer"

    /** Message, watch → phone. Carries the transfer outcome for one stream channel. */
    const val STREAM_TRANSFER_ACK = "/fms/watch/stream_transfer_ack"

    /**
     * Channel, either direction. Carries the bytes of one file sent to the paired watch (S1861).
     *
     * The file name rides in the path as a trailing segment ("$FILE_TRANSFER/photo.jpg"): the watch
     * half already names the received file from the last segment, so the channel needs no separate
     * metadata message to land the bytes under the right name.
     */
    const val FILE_TRANSFER = "/fms/transfer_file"

    /**
     * Message, either direction. Announces name, size and type of the file the channel will carry
     * (S1861), so the receiving side can refuse an oversized file before a byte is written.
     *
     * A separate path rather than a prefix of [FILE_TRANSFER], so the manifest filter that starts the
     * service for a message cannot be confused with the one that starts it for a channel.
     */
    const val FILE_TRANSFER_META = "/fms/transfer_file_meta"

    /** Message, watch → phone. Carries the correlated outcome of one file transfer. */
    const val FILE_TRANSFER_ACK = "/fms/watch/transfer_file_ack"

    /** Message, phone → watch. Immediate outcome acknowledgement of one received file. */
    const val FILE_RECEIVE_ACK = "/fms/phone/receive_file_ack"

    /** Data Item, phone → watch. Deferred upload outcome of one received file. */
    const val FILE_UPLOAD_OUTCOME = "/fms/phone/receive_file_upload_outcome"

    /** Message, watch → phone. Asks the phone to show one of its own files (S2004). */
    const val OPEN_ON_PHONE_REQUEST = "/fms/watch/open_on_phone"

    /** Message, phone → watch. Answers one open request - shown, notified, or refused. */
    const val OPEN_ON_PHONE_ACK = "/fms/phone/open_on_phone_ack"

    /**
     * Reserved name the watch background frame is transferred under (S2000).
     *
     * The name is the whole correlation mechanism: [FILE_TRANSFER] names the received file from the
     * last path segment and carries no field a purpose could ride in, so the watch recognises the
     * background frame by this name and by nothing else. Sending anything else under it overwrites
     * the background.
     */
    const val BACKGROUND_IMAGE_FILE_NAME = "wear_background.png"

    /**
     * Canonical square edge of that frame, in pixels (S2000).
     *
     * Declared once rather than written at each use, so retargeting another watch display is one
     * edit instead of a search. The phone scales to it before sending; the watch never resizes.
     */
    const val BACKGROUND_IMAGE_EDGE_PX = 480

    // --- WearEventEnvelope.eventType constants ---

    /** eventType value for SETTINGS_PUSH envelopes. */
    const val EVENT_SETTINGS = "SETTINGS_PUSH"

    /** eventType value for SETTINGS_REPORT envelopes (S2093). */
    const val EVENT_SETTINGS_REPORT = "SETTINGS_REPORT"

    /** eventType value for SOURCES_EXPORT envelopes. */
    const val EVENT_SOURCES_EXPORT = "SOURCES_EXPORT"

    /** eventType value for PLAYBACK_STATE envelopes. */
    const val EVENT_PLAYBACK_STATE = "PLAYBACK_STATE"

    /** eventType value for PLAYBACK_CMD envelopes. */
    const val EVENT_PLAYBACK_CMD = "PLAYBACK_CMD"

    /** eventType value for FAVORITES_DELTA envelopes. */
    const val EVENT_FAVORITES = "FAVORITES_DELTA"

    /** eventType value for PHONE_RESOURCE_PAGE envelopes. */
    const val EVENT_PHONE_RESOURCE_PAGE = "PHONE_RESOURCE_PAGE"

    /** eventType value for STREAM_TRANSFER envelopes. */
    const val EVENT_STREAM_TRANSFER = "STREAM_TRANSFER"
}
