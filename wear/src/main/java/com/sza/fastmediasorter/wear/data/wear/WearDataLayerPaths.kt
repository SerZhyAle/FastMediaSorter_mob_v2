package com.sza.fastmediasorter.wear.data.wear

/**
 * Path constants for the Wear OS Data Layer — watch side mirror.
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
}
