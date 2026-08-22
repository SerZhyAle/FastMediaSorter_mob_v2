package com.sza.fastmediasorter.ui.launcher.signal

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1908: the seam between the signal panel, which wants another application's notifications dismissed, and
 * the listener service, which is the only thing that can do it.
 *
 * The panel cannot hold the service. `NotificationListenerService` is instantiated and bound by the system,
 * and unbound the moment the user revokes notification access - a reference kept past that point answers
 * every call with a `SecurityException`. So the service registers a canceller while it is connected and
 * withdraws it when it is not, and [isAvailable] is the panel's honest answer to "can anything be dismissed
 * right now", which strategic §5.1 pillar 4 turns into an absent button rather than a dead one.
 *
 * Nothing here carries notification content: a key is all that a dismissal needs, and a key is all this
 * class accepts - the same shape rule [ForeignNotificationCounts] is built on (S1465 ADR-2).
 */
@Singleton
class ForeignNotificationDismisser @Inject constructor() {

    /**
     * Written from the system's listener thread on (dis)connect and read from the main thread on a tap, so
     * every access is serialised. Held as a function rather than as the service so this class never gains a
     * way to reach the rest of the service's API.
     */
    private var cancel: ((Array<String>) -> Unit)? = null

    private val monitor = Any()

    /** True while a connected listener is standing by to cancel; false the instant access is revoked. */
    val isAvailable: Boolean
        get() = synchronized(monitor) { cancel != null }

    fun register(cancel: (Array<String>) -> Unit) {
        synchronized(monitor) { this.cancel = cancel }
    }

    fun unregister() {
        synchronized(monitor) { cancel = null }
    }

    /**
     * Dismisses exactly [keys] and nothing else.
     *
     * An empty set is not an error and is not "dismiss everything": a row whose notifications vanished
     * between the render and the tap legitimately resolves to no keys, and the system's own
     * `cancelAllNotifications` is deliberately never called - strategic §6.3 limits every action here to
     * foreign notifications the panel actually listed.
     */
    fun dismiss(keys: Collection<String>) {
        if (keys.isEmpty()) {
            return
        }
        val target = synchronized(monitor) { cancel }
        if (target == null) {
            Timber.d("Foreign notification dismiss: no connected listener, ignoring %d key(s)", keys.size)
            return
        }
        runCatching { target(keys.toTypedArray()) }
            .onFailure { Timber.w(it, "Foreign notification dismiss failed for %d key(s)", keys.size) }
    }
}
