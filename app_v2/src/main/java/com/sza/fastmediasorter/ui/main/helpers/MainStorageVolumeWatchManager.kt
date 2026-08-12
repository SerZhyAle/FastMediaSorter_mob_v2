package com.sza.fastmediasorter.ui.main.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber

/**
 * S1378: keeps the resource list honest about removable media.
 *
 * A card can be inserted or pulled out while the list is on screen, and neither event reaches the
 * app through the resource store - the platform announces it by broadcast. Without this watcher the
 * list keeps showing a source that is no longer there until the user reopens the screen.
 *
 * Kept out of `MainActivity` per CLAUDE.md Rule 3, and it observes the lifecycle itself rather than
 * being driven from `onStart`/`onStop`: the host then spends a single [attach] call and gains no
 * lifecycle override at all. Both transitions are idempotent, so a repeated edge is harmless.
 */
class MainStorageVolumeWatchManager(
    private val activity: ComponentActivity,
    private val onVolumeChanged: () -> Unit,
) : DefaultLifecycleObserver {

    private var receiver: BroadcastReceiver? = null

    /**
     * Binds to the host's lifecycle. The subscription is taken and released on this side so the two
     * halves sit in one file - the host is left with a single call and no lifecycle code at all.
     */
    fun attach() {
        activity.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = start()

    override fun onStop(owner: LifecycleOwner) = stop()

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
    }

    fun start() {
        if (receiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Timber.i("MainStorageVolumeWatchManager: volume event %s", intent?.action)
                onVolumeChanged()
            }
        }
        ContextCompat.registerReceiver(
            activity,
            listener,
            mediaEventFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiver = listener
    }

    fun stop() {
        val listener = receiver ?: return
        receiver = null
        try {
            activity.unregisterReceiver(listener)
        } catch (e: IllegalArgumentException) {
            // The platform answers an already-gone registration with this, which is a state to note
            // rather than a failure: the screen is going away either way.
            Timber.w(e, "MainStorageVolumeWatchManager: receiver was not registered")
        }
    }

    /**
     * The media broadcasts carry a `file://` data URI, so a filter without the scheme matches
     * nothing at all - the most common way this kind of receiver silently never fires.
     */
    private fun mediaEventFilter() = IntentFilter().apply {
        addAction(Intent.ACTION_MEDIA_MOUNTED)
        addAction(Intent.ACTION_MEDIA_UNMOUNTED)
        addAction(Intent.ACTION_MEDIA_EJECT)
        addAction(Intent.ACTION_MEDIA_REMOVED)
        addDataScheme(SCHEME_FILE)
    }

    private companion object {
        const val SCHEME_FILE = "file"
    }
}
