package com.sza.fastmediasorter.core.ui.focus

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import java.util.WeakHashMap

/**
 * S0943: attaches one [FocusDecorationController] to every Activity window, registered once in
 * `FastMediaSorterApp`. This blankets all screens (every `BaseActivity` subclass and direct
 * `AppCompatActivity`) with the in-place D-pad/TV focus decoration without per-Activity edits.
 *
 * A [WeakHashMap] keyed by Activity avoids retaining a destroyed Activity if `onActivityDestroyed`
 * is ever skipped; the controller is still explicitly detached there to remove its listeners.
 */
class FocusDecorationActivityCallbacks : Application.ActivityLifecycleCallbacks {

    private val controllers = WeakHashMap<Activity, FocusDecorationController>()

    // One fragment-callbacks registration per FragmentActivity so DialogFragment / bottom-sheet
    // windows (their own Window) also get decorated; kept to unregister symmetrically on destroy.
    private val fragmentCallbacks = WeakHashMap<Activity, FocusDecorationFragmentCallbacks>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Opt-out for screens that manage their own focus surface (e.g. VR), see FocusDecorationExcluded.
        if (activity is FocusDecorationExcluded) return
        val controller = FocusDecorationController(activity.window)
        controller.attach()
        controllers[activity] = controller

        if (activity is FragmentActivity) {
            val callbacks = FocusDecorationFragmentCallbacks()
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
