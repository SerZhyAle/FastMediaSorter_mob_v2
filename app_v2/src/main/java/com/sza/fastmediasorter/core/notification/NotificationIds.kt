package com.sza.fastmediasorter.core.notification

/**
 * Single registry of every notification id this app posts.
 *
 * Android keys a notification by (package, tag, id) and none of these posters use a tag, so two
 * owners sharing an id silently replace each other's notification - and either one's
 * `stopForeground(STOP_FOREGROUND_REMOVE)` or WorkManager teardown then cancels it for both.
 * S1292 found two live collisions this way (screen recording vs the gesture-overlay host, and the
 * periodic sync worker vs the S0710 permission advisory), so ids are declared here instead of
 * privately per class, and `NotificationIdsTest` fails the build if two ever match again.
 *
 * Each class keeps its own `NOTIFICATION_ID` alias pointing here, so call sites stay unchanged.
 */
object NotificationIds {

    /** Media3 playback notification owned by AudioPlaybackService. */
    const val MEDIA_PLAYBACK = 1001

    /** Progress/result notification of the scheduled-operations worker. */
    const val SCHEDULED_OPERATIONS = 4200

    /** S0710 one-shot advisory telling the user to grant All-Files access; must outlive any worker run. */
    const val SCHEDULED_OPERATIONS_PERMISSION = 4201

    /** Foreground notification of the periodic network-resource sync worker. */
    const val NETWORK_FILES_SYNC = 4202

    /** Foreground notification of the duplicate-detection worker. */
    const val DUPLICATE_DETECTION = 4203

    /** S1802: one log report arrived from the paired watch and is ready to send on. */
    const val WATCH_LOG_REPORT = 4204

    /** S2044: upload of one file received from the watch into a network or cloud destination. */
    const val WEAR_RECEIVED_FILE_UPLOAD = 4205

    /** S2004: the watch asked this phone to show one of its own files while the app was not in front. */
    const val WEAR_OPEN_ON_PHONE = 4206

    /** S2142: a file the watch sent here to be handed on to one of this phone's «Send to..» receivers. */
    const val WEAR_SEND_TO_FROM_WATCH = 4207

    /** MediaProjection screenshot capture service. */
    const val SCREEN_CAPTURE = 0x4053

    /** Edge-gesture overlay host service; re-posted on every process foreground. */
    const val GESTURE_OVERLAY_HOST = 0x4054

    /** Screen+audio video recording service, including its pause/resume updates. */
    const val SCREEN_VIDEO_RECORDING = 0x4055

    /** Quick audio recorder widget service. */
    const val QUICK_AUDIO_RECORDER = 0xA349

    /**
     * SaveFallbackNotifier derives one id per rescued file by adding an offset to this base, so the
     * whole 0x5A22xxxx block is reserved. Keep every other id well below it.
     */
    const val SAVE_FALLBACK_BASE = 0x5A220000
}
