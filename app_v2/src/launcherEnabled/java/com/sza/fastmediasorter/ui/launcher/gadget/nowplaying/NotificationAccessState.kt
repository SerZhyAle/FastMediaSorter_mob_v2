package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * S0429: whether this app currently holds notification access, and where the user grants it.
 *
 * Read fresh on every call, never cached: the access lives in system settings and the user can revoke
 * it while the launcher is on screen, and a cached answer would leave the gadget claiming an access it
 * no longer has - showing a foreign session it can no longer see.
 */
object NotificationAccessState {

    fun isEnabled(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
