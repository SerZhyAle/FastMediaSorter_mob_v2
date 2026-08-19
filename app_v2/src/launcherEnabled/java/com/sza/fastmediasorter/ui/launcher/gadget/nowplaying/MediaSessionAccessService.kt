package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.launcher.signal.ForeignNotificationCounts
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

/**
 * S0429: the component that buys the right to read other apps' media sessions.
 * S1465: and, since the user can now ask for it, the component that counts their pending notifications.
 *
 * `MediaSessionManager.getActiveSessions` refuses to answer unless it is handed an enabled
 * `NotificationListenerService` the system trusts, which is why this class exists at all. It stayed empty
 * for as long as that was its only job, and its KDoc named the emptiness as the proof that it never read a
 * notification.
 *
 * **That proof is retired here, deliberately and in writing** - S1465 gives the launcher strip a row of
 * foreign application icons, and something has to see the notifications to count them. What replaces it:
 *
 * - **What this service reads:** the posting package and the notification key, plus the one bit saying
 *   whether the system marked a notification as a group summary.
 * - **What it still never reads:** the title, the text, the extras, the actions, the attachments. Not by
 *   promise - [ForeignNotificationCounts] has no member that would accept any of them, so a later edit that
 *   tried to pass content through would not compile.
 * - **A second listener service is deliberately not declared.** The system asks the user's consent per
 *   component, so a second one would send them back to the same system screen to grant the same access
 *   twice; the permission registry also holds one row per manifest permission, and its parity test would
 *   refuse the duplicate (S1465 ADR-3).
 */
@AndroidEntryPoint
class MediaSessionAccessService : NotificationListenerService() {

    @Inject
    lateinit var counts: ForeignNotificationCounts

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * S1465 ADR-4: the system binds this service for as long as the notification access is granted, because
     * the "now playing" gadget needs it - so the user's own switch, not the grant, is what decides whether
     * anything is counted. Followed here rather than in the signal source so that a disabled capability
     * holds nothing at all, instead of holding counts it merely declines to show.
     */
    override fun onCreate() {
        super.onCreate()
        settingsRepository.getSettings()
            .map { it.launcherForeignNotificationsEnabled }
            .distinctUntilChanged()
            .onEach(::applyEnabled)
            .launchIn(serviceScope)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Switching on has to seed from what is already posted: [onListenerConnected] delivered that set once,
     * long before the user reached the switch, and it does not fire again - without this the strip would
     * stay empty until some application happened to post something new.
     */
    private fun applyEnabled(enabled: Boolean) {
        counts.setEnabled(enabled)
        if (!enabled) {
            return
        }
        // The setting can flip while the system has not (re)connected this listener, and asking an
        // unconnected listener for the active set is refused rather than answered with nothing.
        try {
            counts.reset(activeNotifications.orEmpty().map(::toPosted))
        } catch (notConnected: SecurityException) {
            Timber.w(notConnected, "Listener not connected yet - counts will seed on the next connection")
        }
    }

    /**
     * The system delivers the currently posted set here, and only here, after each (re)connection. Taken as
     * a replacement rather than a merge: while this service was disconnected the app saw nothing, so any
     * count carried over from before would describe a shade that has since moved on.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        counts.reset(activeNotifications.orEmpty().map(::toPosted))
    }

    /**
     * The counterpart of [onListenerConnected]: with the listener gone the app has no way to learn that a
     * notification was dismissed, so keeping the last known counts would leave the strip showing icons for
     * notifications the user may have cleared minutes ago.
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        counts.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn?.let(::toPosted) ?: return
        counts.onPosted(posted.packageName, posted.key, posted.isGroupSummary)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val removed = sbn ?: return
        counts.onRemoved(removed.packageName, removed.key)
    }

    /**
     * The one place a `StatusBarNotification` is touched at all, reducing it immediately to the three facts
     * that may leave this class. Keeping the narrowing here means the callbacks above hold nothing wider.
     */
    private fun toPosted(sbn: StatusBarNotification) = ForeignNotificationCounts.PostedNotification(
        packageName = sbn.packageName,
        key = sbn.key,
        isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
    )
}
