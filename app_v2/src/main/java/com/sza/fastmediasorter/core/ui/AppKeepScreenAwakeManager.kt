package com.sza.fastmediasorter.core.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the global prevent-sleep setting to foreground Activity hosts that do not inherit
 * [BaseActivity]. BaseActivity keeps ownership of its own player and screen-specific exceptions.
 */
@Singleton
class AppKeepScreenAwakeManager @Inject constructor(
    @ApplicationScope scope: CoroutineScope,
    settingsRepository: SettingsRepository,
) : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var preventSleep = false

    private var resumedActivity: WeakReference<Activity>? = null

    init {
        scope.launch {
            settingsRepository.getSettings()
                .map { it.preventSleep }
                .distinctUntilChanged()
                .collect { enabled ->
                    preventSleep = enabled
                    withContext(Dispatchers.Main.immediate) {
                        resumedActivity?.get()?.let(::applyTo)
                    }
                }
        }
    }

    private fun applyTo(activity: Activity) {
        if (activity is BaseActivity<*>) return
        // S2536: the same stand-down BaseActivity applies, for the hosts that do not inherit it. The
        // level is read here rather than cached with preventSleep so a resume after the charge
        // recovered gets the current answer without this class observing the level itself.
        Timber.d("S3285: plain host ${activity::class.simpleName}, preventSleep=$preventSleep")
        if (KeepScreenAwakePolicy.shouldKeepScreenAwake(preventSleep)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = applyTo(activity)

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
        applyTo(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity?.get() === activity) resumedActivity = null
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
