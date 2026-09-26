package com.sza.fastmediasorter.wear.data.wear

/**
 * Path constants for the Wear OS Data Layer - watch side mirror.
 * String values are identical to the phone-side WearDataLayerPaths to guarantee path matching.
 * The compatible network-sources routes remain in this catalogue even though their payloads predate
 * the event envelope: both modules still need one mechanically checked declaration for every route.
 */
object WearDataLayerPaths {

    /** Data Item, phone → watch. Carries watch companion settings payload. */
    const val SETTINGS_PUSH = "/fms/wear/settings"

    /**
     * Data Item, phone to watch. Carries the launcher clock gadget's look and wallpaper palette (S3557).
     *
     * Its own path rather than a field of [SETTINGS_PUSH]: that bundle leaves the phone only on a
     * button, and a gesture on the gadget has to reach the watch by itself. Under the `/fms/wear`
     * prefix the noLegal manifest already declares for the listener.
     *
     * Mirrored verbatim from the phone module's copy of this object - the two must not drift.
     */
    const val CLOCK_STYLE = "/fms/wear/clock_style"

    /**
     * Data Item, phone to watch. Carries the four watch-face slot choices (S3558).
     *
     * Its own path for [CLOCK_STYLE]'s reason: a choice on the phone must reach the face without the
     * settings push button. Mirrored verbatim from the phone module's copy of this object.
     */
    const val FACE_SLOTS = "/fms/wear/face_slots"

    /** Message, watch → phone. Carries network sources export payload. */
    const val SOURCES_EXPORT = "/fms/watch/sources_export"

    /** Message, watch → phone. Requests the compatible network-sources sync. */
    const val NETWORK_SOURCES_REQUEST = "/fms/network_sources/request"

    /**
     * Data Item, phone → watch. Carries the compatible network-sources payload.
     *
     * The batch states both halves of the owner's choice (S2882): the sources that belong on this watch
     * AND the ids the phone declares as not belonging here, which is what lets unticking a box there
     * withdraw a source here. [STREAM_PINS] and [SEND_TO_RECEIVERS] reach the same end by replacing
     * their set whole, and that reasoning does NOT transfer to this route - this catalogue is edited on
     * the watch too, and sources created here carry ids the phone never issued, so reading an absence
     * from the batch as a withdrawal would delete them.
     */
    const val NETWORK_SOURCES_PUSH = "/fms/network_sources/push"

    /** Message, watch → phone. Acknowledges the compatible network-sources payload. */
    const val NETWORK_SOURCES_ACK = "/fms/network_sources/ack"

    /**
     * Data Item, watch → phone. Carries the watch's own settings payload back to the phone (S2093).
     *
     * A Data Item rather than a Message, matching [PLAYBACK_STATE]: the watch's settings are state, so
     * the phone must be able to read the latest one after reconnecting rather than only catching it live.
     * The watch only sends this path, so it needs no listener filter of its own.
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

    /**
     * Message, watch → phone. Asks the phone to remove the original this watch has just copied (S3359).
     *
     * Sent only after the watch verified the published copy's length, and it carries that length, so
     * the phone can refuse a token that now addresses a different file (strategic ADR-4).
     *
     * Hand-mirrored from the phone's copy of this object - the modules share no code, so this literal
     * is the entire contract and one differing character produces a message nobody receives.
     */
    const val PHONE_RESOURCE_DELETE_REQUEST = "/fms/watch/phone_resource/delete"

    /**
     * Data Item, phone → watch. Answers one delete request - removed, or kept with the reason (S3359).
     *
     * A Data Item and not a message for one reason: the companion-off answer has to reach this watch,
     * and an outgoing message is swallowed by that very switch. The phone publishes the refusal through
     * the helper that bypasses it, so the two answers travel one way instead of two.
     */
    const val PHONE_RESOURCE_DELETE_ACK = "/fms/phone/phone_resource/delete_ack"

    /** Message, watch → phone. Carries one log report for the developer. */
    const val LOG_REPORT_REQUEST = "/fms/watch/log_report"

    /** Message, phone → watch. Answers one log report - accepted, or refused with a reason. */
    const val LOG_REPORT_ACK = "/fms/phone/log_report_ack"

    /**
     * Message, watch → phone. Carries this watch's system-information report as flat text (S3108).
     *
     * Rendered here rather than on the phone: every label in the report is a string resource of this
     * module, which the phone cannot resolve at all.
     *
     * Hand-mirrored from the phone's copy of this object - the modules share no code, so this literal
     * is the entire contract and one differing character produces a message nobody receives.
     */
    const val SYSTEM_INFO_REPORT = "/fms/watch/system_info_report"

    /** Message, phone → watch. Answers one system-information report - stored, or refused with a reason. */
    const val SYSTEM_INFO_REPORT_ACK = "/fms/phone/system_info_report_ack"

    /**
     * Message, watch → phone. Carries this watch's text clipboard (S3109).
     *
     * Under the `/fms/watch` prefix the phone already declares for its listener, so it needs no
     * manifest edit; a path outside a declared prefix is dropped by GMS in silence (S1697).
     *
     * Mirrored verbatim from the phone module's copy of this object - the two must not drift.
     */
    const val CLIPBOARD_TEXT_FROM_WATCH = "/fms/watch/clipboard_text"

    /** Message, phone → watch. Answers one watch clipboard - taken, or refused with a reason. */
    const val CLIPBOARD_TEXT_FROM_WATCH_ACK = "/fms/phone/clipboard_text_ack"

    /**
     * Message, phone → watch. Carries the phone's text clipboard (S3109).
     *
     * ADR-1 decides the direction rather than symmetry: since Android 10 only the foreground app may
     * read its own clipboard, so the side whose clipboard is read is always the side that starts, and
     * this watch has no route by which to ask for the text.
     *
     * Mirrored verbatim from the phone module's copy of this object - the two must not drift.
     */
    const val CLIPBOARD_TEXT_FROM_PHONE = "/fms/phone/clipboard_text"

    /** Message, watch → phone. Answers one phone clipboard - taken, or refused with a reason. */
    const val CLIPBOARD_TEXT_FROM_PHONE_ACK = "/fms/watch/clipboard_text_ack"

    /**
     * Message, phone → watch. Asks this watch for a picture of its own screen (S3110).
     *
     * The image never rides this route: a Data Layer message is capped at 100 KB, so the answer sends
     * the PNG over [FILE_TRANSFER] and this path carries only the ask.
     *
     * Mirrored verbatim from the phone module's copy of this object - the two must not drift.
     */
    const val SCREENSHOT_REQUEST = "/fms/phone/screenshot_request"

    /**
     * Message, watch → phone. Answers one screenshot request - captured, or refused with a reason.
     *
     * It carries the name of the file this watch sent, because the phone receives that file through
     * the generic transfer route and has nothing else with which to tie an arrival to its request.
     */
    const val SCREENSHOT_REQUEST_ACK = "/fms/watch/screenshot_request_ack"

    /**
     * Message, watch → phone. Starts the phone's distress signal in the mode this watch chose (S3216).
     *
     * The payload is the `SosMode` member name and nothing else - the two modules share no source, so
     * those names are the whole wire contract. Mirrored verbatim from the phone module's copy of this
     * object; one differing character produces a message nobody receives.
     */
    const val SOS_START_FROM_WATCH = "/fms/watch/sos/start"

    /** Message, watch → phone. Ends the phone's distress signal, whichever device started it (S3216). */
    const val SOS_STOP_FROM_WATCH = "/fms/watch/sos/stop"

    /**
     * Message, phone → watch. Starts this watch's distress signal in the mode the phone chose (S3216).
     *
     * Under the `/fms/phone` prefix `wear/src/main/AndroidManifest.xml` already declares, so it needs no
     * manifest edit; a path outside a declared prefix is dropped by GMS in silence (S1697).
     */
    const val SOS_START_FROM_PHONE = "/fms/phone/sos/start"

    /** Message, phone → watch. Ends this watch's distress signal, whichever device started it (S3216). */
    const val SOS_STOP_FROM_PHONE = "/fms/phone/sos/stop"

    /** Message, phone → watch. Carries one stream channel description to store on the watch. */
    const val STREAM_TRANSFER = "/fms/phone/stream_transfer"

    /** Message, watch → phone. Carries the transfer outcome for one stream channel. */
    const val STREAM_TRANSFER_ACK = "/fms/watch/stream_transfer_ack"

    /**
     * Data Item, phone → watch. Carries the whole set of pinned stream identities (S2149).
     *
     * A Data Item rather than a Message because the set is state, not an event: a watch switched on a
     * day later must see the current set rather than have missed the moment it changed. The set is
     * always sent whole - never a delta and never skipped when empty - because replacing it is the only
     * thing that lets an unpin on the phone withdraw the channel from this watch's top group.
     *
     * Mirrored verbatim from the phone module's copy of this object - the two must not drift.
     */
    const val STREAM_PINS = "/fms/phone/stream_pins"

    /**
     * Data Item, phone → watch. Carries the whole set of «Send to..» receivers this watch may offer
     * (S2142).
     *
     * A Data Item and a whole set for [STREAM_PINS]'s reasons: the list is state this watch must hold
     * while out of reach, and replacing it whole is the only thing that lets a receiver switched off
     * on the phone disappear from here. Its own path rather than a field of the settings payload,
     * because the list is a derivative of the owner's settings and not a setting (ADR-5).
     *
     * Mirrored verbatim from the phone module's copy of this object - the two must not drift.
     */
    const val SEND_TO_RECEIVERS = "/fms/phone/send_to_receivers"

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

    /** Message, watch → phone. Asks the phone to cast the content this watch is showing (S2531). */
    const val CAST_REQUEST = "/fms/watch/cast_request"

    /** Message, watch → phone. Asks the phone to end the cast session it is running (S2531). */
    const val CAST_STOP = "/fms/watch/cast_stop"

    /** Message, phone → watch. Answers one cast request - casting, picker needed, or refused. */
    const val CAST_ACK = "/fms/phone/cast_ack"

    /** Message, phone → watch. The phone's current cast session, which this watch only displays. */
    const val CAST_STATE = "/fms/phone/cast_state"

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

    /** Message, watch → phone. Carries stream pins delta payload (S2497). */
    const val STREAM_PINS_DELTA = "/fms/watch/stream_pins_delta"

    /**
     * Message, phone → watch. Asks this watch to let the phone listen to its microphone (S2550).
     *
     * It carries no audio and never can: ADR-1 keeps every byte of sound on the watch's own LAN
     * server, because the Data Layer's Bluetooth path is below the project's floor for audio and its
     * Wi-Fi path routes through a node on Google servers.
     *
     * Under the `/fms/phone` prefix `wear/src/main/AndroidManifest.xml` already declares, so it needs
     * no manifest edit; a path outside a declared prefix is dropped by GMS in silence (S1697).
     */
    const val LISTEN_START = "/fms/phone/listen_start"

    /** Message, phone → watch. Ends the listening session - server, microphone and notification. */
    const val LISTEN_STOP = "/fms/phone/listen_stop"

    /**
     * Message, watch → phone. Answers one listen command with an address, or with a refusal (S2550).
     *
     * ADR-2: this answer is why no discovery is built - the watch reports its own host and port here.
     * A refusal rides the same payload rather than arriving as silence, so the phone never has to tell
     * "refused" from "lost".
     */
    const val LISTEN_ACK = "/fms/watch/listen_ack"

    /**
     * Message, watch → phone. Asks the paired phone to serve its camera and microphone (S2551).
     *
     * Under the `/fms/watch` prefix `app_v2/src/wearGms/AndroidManifest.xml` already declares for
     * `PhoneWearListenerService`, so it needs no manifest edit; a path outside a declared prefix is
     * dropped by GMS in silence (S1697).
     *
     * Hand-mirrored from the phone's `WearDataLayerPaths.kt` - the modules share no code, so this
     * literal is the entire contract and one differing character produces a message nobody receives.
     */
    const val CAMERA_VIEW_START = "/fms/watch/camera_view_start"

    /** Message, watch → phone. Ends the camera session - server, camera and notification (S2551). */
    const val CAMERA_VIEW_STOP = "/fms/watch/camera_view_stop"

    /** Message, watch → phone. Switches the live lens without ending the session (S2551). */
    const val CAMERA_VIEW_SWITCH = "/fms/watch/camera_view_switch"

    /**
     * Message, phone → watch. Answers one camera command with a URL, or with a refusal (S2551).
     *
     * Under the `/fms/phone` prefix `wear/src/main/AndroidManifest.xml` already declares. The address
     * arrives as a finished URL rather than a host and a port, which is what keeps this wire
     * independent of the phone's transport choice; a refusal rides the same payload rather than
     * arriving as silence, so the watch never has to tell "refused" from "lost".
     */
    const val CAMERA_VIEW_ACK = "/fms/phone/camera_view_ack"

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

    /** eventType value for STREAM_PINS envelopes (S2149). */
    const val EVENT_STREAM_PINS = "STREAM_PINS"

    /** eventType value for CLOCK_STYLE envelopes (S3557). */
    const val EVENT_CLOCK_STYLE = "CLOCK_STYLE"

    /** eventType value for FACE_SLOTS envelopes (S3558). */
    const val EVENT_FACE_SLOTS = "FACE_SLOTS"

    /** eventType value for STREAM_PINS_DELTA envelopes (S2497). */
    const val EVENT_STREAM_PINS_DELTA = "STREAM_PINS_DELTA"

    /** eventType value for SEND_TO_RECEIVERS envelopes (S2142). */
    const val EVENT_SEND_TO_RECEIVERS = "SEND_TO_RECEIVERS"
}
