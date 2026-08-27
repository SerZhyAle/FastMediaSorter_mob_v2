package com.sza.fastmediasorter.wear.data.wear

/**
 * Path constants for the Wear OS Data Layer - watch side mirror.
 * String values are identical to the phone-side WearDataLayerPaths to guarantee path matching.
 * Existing /fms/network_sources/ paths are defined locally in WatchWearListenerService
 * for backward compatibility and are not listed here.
 */
object WearDataLayerPaths {

    /** Data Item, phone → watch. Carries watch companion settings payload. */
    const val SETTINGS_PUSH = "/fms/wear/settings"

    /** Message, watch → phone. Carries network sources export payload. */
    const val SOURCES_EXPORT = "/fms/watch/sources_export"

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
     * Channel, either direction. Carries the bytes of one transferred file (S1861).
     *
     * The file name rides as the trailing segment ("$FILE_TRANSFER/photo.jpg") - the Data Layer gives
     * a channel no other handle the announcing message could be correlated by.
     */
    const val FILE_TRANSFER = "/fms/transfer_file"

    /**
     * Message, either direction. Announces name, size and type of the file the channel will carry.
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
     * Reserved name the watch background frame arrives under (S2000).
     *
     * The name is the whole correlation mechanism: [FILE_TRANSFER] names the received file from the
     * last path segment and carries no field a purpose could ride in, so the background frame is
     * recognised by this name and by nothing else. Mirrored verbatim in the phone module's copy of
     * this object - the two must not drift.
     */
    const val BACKGROUND_IMAGE_FILE_NAME = "wear_background.png"

    /**
     * Canonical square edge of that frame, in pixels (S2000).
     *
     * The phone scales to it before sending, so the watch never resizes what it receives. Declared
     * once rather than written at each use, so retargeting another display is one edit.
     */
    const val BACKGROUND_IMAGE_EDGE_PX = 480

    // --- WearEventEnvelope.eventType constants ---

    /** eventType value for SETTINGS_PUSH envelopes. */
    const val EVENT_SETTINGS = "SETTINGS_PUSH"

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
