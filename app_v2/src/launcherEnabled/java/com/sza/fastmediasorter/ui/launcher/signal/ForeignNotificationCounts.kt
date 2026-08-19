package com.sza.fastmediasorter.ui.launcher.signal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1465 ADR-2: how many notifications each application currently has posted - and nothing else about them.
 *
 * The owner ruled on 2026-08-17 for icon and count only, with no notification text read or shown anywhere.
 * That ruling is enforced here by shape rather than by discipline: no method on this class accepts a title,
 * a text or any notification extra, so a later edit cannot quietly start carrying content through it without
 * changing the signature a reviewer reads first.
 *
 * **The counting rule, because "how many" has more than one defensible answer.** One entry per distinct
 * notification key, matching what the system's own shade lists:
 * - **Group summaries are not counted.** A summary stands for its children and is posted beside them, so
 *   counting both reports every grouped conversation twice.
 * - **Ongoing notifications are counted.** The shade shows them, and a playing track or a running download
 *   is exactly the kind of pending state the strip exists to surface.
 * - **This app's own package is never counted.** The strip already reports this app's work through its own
 *   signal sources, and counting it here would show the same activity twice, in two different shapes.
 */
@Singleton
class ForeignNotificationCounts @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Notification keys per package. Keys rather than a bare tally because the system re-posts an existing
     * notification to update it, and a counter that only incremented would climb forever on a download that
     * refreshes its progress once a second.
     */
    private val keysByPackage = mutableMapOf<String, MutableSet<String>>()

    /**
     * S1465 ADR-4: the capability's own switch, independent of the system grant. Guarded by the same monitor
     * as the map because a callback arriving while the user is switching the feature off must either be
     * recorded before the clear or dropped after it - never land in a map that was just emptied.
     */
    private var isEnabled = false

    private val mutableCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Per-package pending counts, empty while nothing is posted or the capability is off. */
    val counts: StateFlow<Map<String, Int>> = mutableCounts.asStateFlow()

    /**
     * Records that [packageName] has a notification identified by [key] posted.
     *
     * @param isGroupSummary whether the system marked this notification as the summary of a group. A flag,
     * not the notification: the decision needs one bit, and passing the object would put its content within
     * reach of this class for the first time.
     */
    fun onPosted(packageName: String, key: String, isGroupSummary: Boolean) {
        if (isGroupSummary || isOwnPackage(packageName)) {
            return
        }
        mutate { if (isEnabled) it.getOrPut(packageName) { mutableSetOf() }.add(key) else false }
    }

    /**
     * Turns the counting on or off, following the user's switch.
     *
     * Switching off drops everything already counted rather than merely hiding it: strategic §11.1 requires
     * that nothing about another application's notifications is held while the capability is off, and a map
     * kept "just in case the user turns it back on" would hold exactly that.
     */
    fun setEnabled(enabled: Boolean) {
        synchronized(keysByPackage) {
            if (isEnabled == enabled) {
                return
            }
            isEnabled = enabled
            if (!enabled && keysByPackage.isNotEmpty()) {
                keysByPackage.clear()
                publish()
            }
        }
    }

    fun onRemoved(packageName: String, key: String) {
        mutate { keys ->
            val remaining = keys[packageName] ?: return@mutate false
            val removed = remaining.remove(key)
            if (remaining.isEmpty()) {
                keys.remove(packageName)
            }
            removed
        }
    }

    /**
     * Replaces everything known with [posted], the way the system hands over the currently active set when a
     * listener connects. A replacement rather than a merge: the listener is disconnected while the app has
     * no access, and notifications posted or dismissed during that gap were never seen, so anything kept
     * from before would be a count of a state that has already moved on.
     */
    fun reset(posted: List<PostedNotification>) {
        val rebuilt = mutableMapOf<String, MutableSet<String>>()
        posted.asSequence()
            .filterNot { it.isGroupSummary || isOwnPackage(it.packageName) }
            .forEach { rebuilt.getOrPut(it.packageName) { mutableSetOf() }.add(it.key) }
        synchronized(keysByPackage) {
            keysByPackage.clear()
            if (isEnabled) {
                keysByPackage.putAll(rebuilt)
            }
            publish()
        }
    }

    /** Drops everything, for the moment the capability is switched off or the system grant disappears. */
    fun clear() {
        mutate { keys ->
            val had = keys.isNotEmpty()
            keys.clear()
            had
        }
    }

    private fun isOwnPackage(packageName: String): Boolean = packageName == context.packageName

    /**
     * The system delivers listener callbacks on its own thread while the signal source reads [counts] from a
     * collector's, so every mutation is serialised here and published as a fresh immutable map - a shared
     * mutable map handed to a collector would be read while the next callback is writing it.
     */
    private fun mutate(block: (MutableMap<String, MutableSet<String>>) -> Boolean) {
        synchronized(keysByPackage) {
            if (block(keysByPackage)) {
                publish()
            }
        }
    }

    private fun publish() {
        mutableCounts.value = keysByPackage.mapValues { (_, keys) -> keys.size }
    }

    /** One posted notification, reduced to the two facts this class is allowed to know about it. */
    data class PostedNotification(
        val packageName: String,
        val key: String,
        val isGroupSummary: Boolean,
    )
}
