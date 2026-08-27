package com.sza.fastmediasorter.wear.core.notification

/**
 * S1961: every notification this module can post, in one place.
 *
 * Android keys a notification by (package, id) when no tag is given, so two features picking the same
 * number means the later one replaces the earlier and cancelling either clears both. Until this ticket
 * the watch posted exactly one notification and the number could live as a literal beside it; a second
 * one is what makes a registry the only way to see the collision before a user does.
 *
 * The numbering is deliberately not shared with `core/notification/NotificationIds.kt` in `app_v2` -
 * that file is not on this module's classpath, and the watch installs its own APK, so the two sets
 * address different notification managers and cannot collide.
 */
object WearNotificationIds {

    /** S1862: the recorder's foreground notification, and the transfer that follows it. */
    const val VOICE_RECORDING = 4201

    /** S1961: the phone asked this watch to open something while the app was not on screen. */
    const val OPEN_ON_WATCH = 4202

    /** S2087: a deferred file upload from watch to phone remote destination failed. */
    const val UPLOAD_OUTCOME = 4203
}
