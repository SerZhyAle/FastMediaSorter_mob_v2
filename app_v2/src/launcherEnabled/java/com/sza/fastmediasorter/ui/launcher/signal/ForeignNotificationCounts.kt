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
     * S2734: when each package last gained a notification, as a monotonic sequence number. Published order
     * is this map read from the highest number down, which is what puts a fresh notification at the left
     * edge of the strip. A number rather than a clock: the row needs only the relative order, and a wall
     * clock that steps backwards would reorder chips for no reason the user can see.
     *
     * Guarded by the same monitor as [keysByPackage] - the two are written together on every callback and a
     * reader that saw one without the other would order a set it is not looking at.
     */
    private val orderByPackage = mutableMapOf<String, Long>()

    private var sequence = 0L

    /**
     * S1465 ADR-4: the capability's own switch, independent of the system grant. Guarded by the same monitor
     * as the map because a callback arriving while the user is switching the feature off must either be
     * recorded before the clear or dropped after it - never land in a map that was just emptied.
     */
    private var isEnabled = false

    private val mutableCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * Per-package pending counts, empty while nothing is posted or the capability is off. Ordered from the
     * package that most recently gained a notification to the one that gained it longest ago (S2734).
     */
    val counts: StateFlow<Map<String, Int>> = mutableCounts.asStateFlow()

    /**
     * S1908: the keys behind one package's count, for dismissing exactly the notifications that produced it.
     *
     * A copy, taken under the same monitor every mutation holds: the system delivers listener callbacks on
     * its own thread while the panel reads this from another, so handing out the live set would let a caller
     * iterate a set the next callback is writing.
     *
     * Still content-free. A key identifies a notification without describing it, so this widens what the
     * class hands out without widening what it knows - the shape rule of S1465 ADR-2 is intact.
     */
    fun keysFor(packageName: String): Set<String> = synchronized(keysByPackage) {
        if (!isEnabled) emptySet() else keysByPackage[packageName]?.toSet().orEmpty()
    }

    /** S1908: every key currently counted, across all packages - what "dismiss all" resolves to. */
    fun allKeys(): Set<String> = synchronized(keysByPackage) {
        if (!isEnabled) emptySet() else keysByPackage.values.flatMapTo(mutableSetOf()) { it }
    }

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
        mutate { keys ->
            if (!isEnabled) {
                return@mutate false
            }
            val added = keys.getOrPut(packageName) { mutableSetOf() }.add(key)
            // Only a genuinely new key moves the package to the front. The system re-posts an existing
            // notification to update it - a download refreshing its progress once a second - and that must
            // not keep dragging the same chip back to the left edge.
            if (added) {
                orderByPackage[packageName] = ++sequence
            }
            added
        }
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
                orderByPackage.clear()
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
                // A package with no notifications left owns no position either, so its next notification
                // arrives as a fresh one and takes the left edge.
                orderByPackage.remove(packageName)
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
            orderByPackage.clear()
            if (isEnabled) {
                keysByPackage.putAll(rebuilt)
                // The system hands the active set over oldest first, so re-numbering in that order is the
                // best recency the listener can recover; nothing older is knowable after a reconnect.
                rebuilt.keys.forEach { orderByPackage[it] = ++sequence }
            }
            publish()
        }
    }

    /** Drops everything, for the moment the capability is switched off or the system grant disappears. */
    fun clear() {
        mutate { keys ->
            val had = keys.isNotEmpty()
            keys.clear()
            orderByPackage.clear()
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

    /**
     * S2734: published newest package first, in a map whose iteration order is part of its meaning - the
     * signal source turns that order into each chip's rank, and the strip lays the ranks out left to right.
     */
    private fun publish() {
        mutableCounts.value = keysByPackage.entries
            .sortedByDescending { (packageName, _) -> orderByPackage[packageName] ?: 0L }
            .associateTo(LinkedHashMap()) { (packageName, keys) -> packageName to keys.size }
    }

    /** One posted notification, reduced to the two facts this class is allowed to know about it. */
    data class PostedNotification(
        val packageName: String,
        val key: String,
        val isGroupSummary: Boolean,
    )
}
