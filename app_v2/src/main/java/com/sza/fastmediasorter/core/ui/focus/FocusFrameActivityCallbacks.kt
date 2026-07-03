package com.sza.fastmediasorter.core.ui.focus

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import java.util.WeakHashMap

/**
 * S0819: attaches one [FocusFrameController] to every Activity window, registered once in
 * `FastMediaSorterApp`. This blankets all screens (every `BaseActivity` subclass and direct
 * `AppCompatActivity`) with the travelling D-pad/TV focus frame without per-Activity edits.
 *
 * A [WeakHashMap] keyed by Activity avoids retaining a destroyed Activity if `onActivityDestroyed`
 * is ever skipped; the controller is still explicitly detached there to remove its listeners.
 */
class FocusFrameActivityCallbacks : Application.ActivityLifecycleCallbacks {

    private val controllers = WeakHashMap<Activity, FocusFrameController>()

    // One fragment-callbacks registration per FragmentActivity so DialogFragment / bottom-sheet
    // windows (their own Window) also get the frame; kept to unregister symmetrically on destroy.
    private val fragmentCallbacks = WeakHashMap<Activity, FocusFrameFragmentCallbacks>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Opt-out for screens that manage their own focus surface (e.g. VR), see FocusFrameExcluded.
        if (activity is FocusFrameExcluded) return
        val controller = FocusFrameController(activity.window)
        controller.attach()
        controllers[activity] = controller

        if (activity is FragmentActivity) {
            val callbacks = FocusFrameFragmentCallbacks()
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)
            fragmentCallbacks[activity] = callbacks
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        controllers.remove(activity)?.detach()
        val callbacks = fragmentCallbacks.remove(activity)
        if (callbacks != null && activity is FragmentActivity) {
            activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(callbacks)
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
