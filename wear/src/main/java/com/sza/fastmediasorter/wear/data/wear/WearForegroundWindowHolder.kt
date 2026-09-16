package com.sza.fastmediasorter.wear.data.wear

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Window
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3110: the window the watch is showing right now, or nothing.
 *
 * `PixelCopy` copies a live window, so a screenshot request that arrives while the app is closed has
 * nothing to copy - and that is an answer the phone can be told, not a failure. Registered from
 * [com.sza.fastmediasorter.wear.FastMediaSorterWearApp], the same way the power observer is.
 *
 * The reference is weak deliberately: a strong one would outlive a destroyed activity and hold its
 * whole view tree for the life of the process, which is the leak this watch can least afford.
 */
@Singleton
class WearForegroundWindowHolder @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var resumed: WeakReference<Activity>? = null

    /** Null whenever no activity of this app is on screen, which is most of the time on a watch. */
    fun currentWindow(): Window? = resumed?.get()?.takeUnless { it.isFinishing }?.window

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) {
            resumed = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
