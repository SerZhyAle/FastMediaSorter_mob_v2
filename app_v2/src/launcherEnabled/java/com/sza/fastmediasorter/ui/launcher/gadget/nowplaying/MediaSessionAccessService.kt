package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

import android.service.notification.NotificationListenerService

/**
 * S0429: the component that buys the right to read other apps' media sessions - nothing more.
 *
 * `MediaSessionManager.getActiveSessions` refuses to answer unless it is handed an enabled
 * `NotificationListenerService` the system trusts, so this class exists purely to be that component.
 * It overrides no callback on purpose: it must never read a notification, and an empty body is the
 * only way to make that true by construction rather than by promise.
 */
class MediaSessionAccessService : NotificationListenerService()
