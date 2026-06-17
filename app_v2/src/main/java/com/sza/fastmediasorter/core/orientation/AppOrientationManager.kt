package com.sza.fastmediasorter.core.orientation

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.ui.SelfManagedScreenOrientation
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * S0439: applies the program-wide "follow OS rotation" policy to every foreground activity that does
 * not manage its own orientation. Player-family activities implement [SelfManagedScreenOrientation]
 * and are skipped so this never fights ScreenRotationManager.
 *
 * Program flag ON  -> SCREEN_ORIENTATION_UNSPECIFIED (delegate to the OS auto-rotate setting).
 * Program flag OFF -> SCREEN_ORIENTATION_LOCKED (do not follow the OS).
 * No accelerometer -> leave the manifest default untouched (matches the rotation UI being hidden).
 *
 * Registered as [Application.ActivityLifecycleCallbacks] from the Application's onCreate.
 */
@Singleton
class AppOrientationManager @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
    settingsRepository: SettingsRepository,
) : Application.ActivityLifecycleCallbacks {

    private val hasAccelerometer: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)

    @Volatile
    private var programFollowSystemRotation: Boolean = true

    // The currently resumed non-self-managed activity, so a settings change can re-apply live.
    private var resumedActivity: WeakReference<Activity>? = null

    init {
        scope.launch {
            settingsRepository.getSettings()
                .map { it.programFollowSystemRotation }
                .distinctUntilChanged()
                .collect { follow ->
                    programFollowSystemRotation = follow
                    // requestedOrientation must be touched on the main thread.
                    withContext(Dispatchers.Main) {
                        resumedActivity?.get()?.let(::applyTo)
                    }
                }
        }
    }

    private fun applyTo(activity: Activity) {
        if (activity is SelfManagedScreenOrientation) return
        // No sensor: leave whatever the manifest declares; the rotation settings UI is hidden too.
        if (!hasAccelerometer) return
        val orientation = if (programFollowSystemRotation) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
        if (activity.requestedOrientation != orientation) {
            activity.requestedOrientation = orientation
            Timber.d(
                "AppOrientationManager: ${activity.javaClass.simpleName} -> " +
                    "orientation=$orientation (programFollowSystemRotation=$programFollowSystemRotation)"
            )
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

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
