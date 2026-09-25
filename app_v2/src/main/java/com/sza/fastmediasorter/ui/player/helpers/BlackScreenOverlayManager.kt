package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.ui.SelfManagedScreenOrientation
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.common.widget.DimHeadingProvider
import com.sza.fastmediasorter.ui.common.widget.DimOverlayView
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockOverlayView
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyleProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusContentProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.di.DimClockEntryPoint
import dagger.Lazy
import dagger.hilt.EntryPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference

class BlackScreenOverlayManager(
    private val activityRef: WeakReference<Activity>,
    private val systemBarsManager: SystemBarsManager,
    var onVisibilityChanged: ((Boolean) -> Unit)? = null,
    private val settingsRepositoryLazy: Lazy<SettingsRepository>? = null,
    private val dimClockStyleProviderLazy: Lazy<DimClockStyleProvider>? = null,
    private val dimStatusContentProviderLazy: Lazy<DimStatusContentProvider>? = null,
    private val headingProviderLazy: Lazy<DimHeadingProvider>? = null,
) {

    var isVisible: Boolean = false
        private set

    /**
     * S2667: true for the whole episode in which this overlay owns the window's system-bar insets -
     * from before the bars are hidden until after they are handed back. A host whose layout is
     * expensive to re-pad reads this to sit the episode out; [isVisible] cannot serve that purpose,
     * because [hide] clears it before restoring the bars and the restore is the costlier half.
     */
    var isChangingSystemBars: Boolean = false
        private set

    private var overlayView: View? = null
    private var dimClockView: DimClockOverlayView? = null
    private var wasFullscreenBeforeOverlay = false
    private var orientationBeforeDim: Int? = null
    private var brightnessBeforeDim: Float? = null

    private fun resolveEntryPoint(context: Context): DimClockEntryPoint =
        EntryPoints.get(context.applicationContext, DimClockEntryPoint::class.java)

    private suspend fun readSettings(activity: Activity): AppSettings {
        val repo = settingsRepositoryLazy?.get() ?: resolveEntryPoint(activity).settingsRepository()
        return repo.getSettings().flowOn(Dispatchers.IO).first()
    }

    fun show() {
        if (isVisible) return
        val activity = activityRef.get() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        wasFullscreenBeforeOverlay = systemBarsManager.isInFullscreenMode()
        isChangingSystemBars = true
        systemBarsManager.enterFullscreenMode()

        val view = DimOverlayView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onExit = { hide() }
            headingLookup = { headingProviderLazy?.get()?.current() }
            setOnKeyListener { _, _, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) {
                    hide()
                    true
                } else {
                    false
                }
            }
            requestFocus()
        }
        decorView.addView(view)
        overlayView = view
        headingProviderLazy?.get()?.setActive(true)

        isVisible = true
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        onVisibilityChanged?.invoke(true)
        resolveDimMode(activity)
    }

    /**
     * S3321: the dim-clock flag is read off the main thread, and the overlay no longer waits for it.
     * A gesture or key handler reaches [show] on Main, where a DataStore read that misses its
     * in-memory cache - the first one after process start, or after a low-memory trim - used to pay
     * disk latency inline. The flag is deliberately not cached on this manager: the user can toggle
     * it in the settings Activity and come back to a host that outlived the visit.
     */
    private fun resolveDimMode(activity: Activity) {
        val scope = (activity as? LifecycleOwner)?.lifecycleScope
        if (scope == null) {
            applyDimMode(activity, clockEnabled = false)
            return
        }
        scope.launch {
            val settings = readSettings(activity)
            // hide() may have won the race while the read was in flight.
            if (!isVisible) return@launch
            applyPlayerRotationPolicy(activity, settings)
            applyDimMode(activity, settings.dimClockOverlayEnabled)
        }
    }

    /**
     * S3369 / S3475: the dim screen turns by the physical sensor, the way a camera stream's frame
     * turns. S3369 routed it through the player's follow-system choice, and with that choice on the
     * system rotation lock kept the launcher's dim screen portrait while the stream on the same phone
     * rotated. Only the player's own "rotation sensor off" still pins it. The host's own request is
     * put back on [hide].
     */
    private fun applyPlayerRotationPolicy(activity: Activity, settings: AppSettings) {
        if (activity is SelfManagedScreenOrientation) return
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) return
        val orientation = dimOrientationFor(settings.playerRotationSensorEnabled)
        Timber.d("S3475: dim orientation request=$orientation host=${activity.javaClass.simpleName}")
        if (orientationBeforeDim == null) orientationBeforeDim = activity.requestedOrientation
        activity.requestedOrientation = orientation
    }

    private fun restoreHostOrientation(activity: Activity) {
        orientationBeforeDim?.let { activity.requestedOrientation = it }
        orientationBeforeDim = null
    }

    private fun applyDimMode(activity: Activity, clockEnabled: Boolean) {
        if (clockEnabled) {
            addClockView(activity)
        } else {
            if (brightnessBeforeDim == null) brightnessBeforeDim = activity.window.attributes.screenBrightness
            setScreenBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        }
    }

    private fun addClockView(activity: Activity) {
        val decorView = activity.window.decorView as? ViewGroup ?: return
        // A hide/show pair can land between the read and this call; a second clock view would be added
        // to the decor view with only the later one reachable for removal.
        if (dimClockView != null) return
        val entryPoint = resolveEntryPoint(activity)
        val styleProvider = dimClockStyleProviderLazy?.get() ?: entryPoint.dimClockStyleProvider()
        val statusProvider = dimStatusContentProviderLazy?.get() ?: entryPoint.dimStatusContentProvider()
        val unitProvider = entryPoint.unitSystemProvider()

        val clockView = DimClockOverlayView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            bind(
                styleProvider,
                statusProvider,
                unitProvider,
                entryPoint.dimChipIconLoader(),
                entryPoint.dimChipActionRouter(),
                entryPoint.dimClockInteractionHandler(),
            )
        }
        decorView.addView(clockView)
        // The overlay owns every touch while dimmed; taps reach the clock only through this forward.
        (overlayView as? DimOverlayView)?.onUserActivity = { clockView.onHostInteraction() }
        // S3366: a chip or battery tap dismisses the dim overlay through the same path an exit
        // gesture takes, before the router starts the intent.
        clockView.onDimExitRequested = { hide() }
        clockView.onUnhandledMotionEvent = { event ->
            (overlayView as? DimOverlayView)?.dispatchTouchEvent(event)
        }
        dimClockView = clockView
    }

    fun onHostConfigurationChanged() {
        if (!isVisible) return
        activityRef.get()?.let { activity ->
            (activity.window.decorView as? ViewGroup)?.let { decorView ->
                dimClockView?.let { clockView ->
                    decorView.removeView(clockView)
                    dimClockView = null
                    addClockView(activity)
                }
            }
        }
    }

    /**
     * For a host that routes touches here instead of through its decor view (the launcher). The clock
     * panel sits above the dim surface, so it is asked first: its clock, chips and battery keep their
     * gestures, and it forwards what it leaves unhandled to the dim surface (S3366, S3369 ADR-1).
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val target: View? = if (isVisible) dimClockView ?: overlayView else null
        target?.dispatchTouchEvent(event)
        return target != null
    }

    fun hide() {
        if (!isVisible) return
        val activity = activityRef.get() ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        dimClockView?.let {
            decorView.removeView(it)
            dimClockView = null
        }
        overlayView?.let { decorView.removeView(it) }
        overlayView = null
        isVisible = false
        headingProviderLazy?.get()?.setActive(false)

        brightnessBeforeDim?.let { setScreenBrightness(activity, it) }
        brightnessBeforeDim = null
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        restoreHostOrientation(activity)

        if (!wasFullscreenBeforeOverlay) {
            systemBarsManager.exitFullscreenMode()
        }
        isChangingSystemBars = false
        onVisibilityChanged?.invoke(false)
        Timber.d("BlackScreenOverlayManager: overlay hidden (restoredFullscreen=${!wasFullscreenBeforeOverlay})")
    }

    fun onFileTypeChanged(isAudioOrVideo: Boolean) {
        if (!isAudioOrVideo && isVisible) hide()
    }

    /**
     * S3256: overrides window screenBrightness to zero when dimmed screen clock is disabled.
     * S3526: un-dim puts back the window's own value from before the dim, not the platform default, so a
     * host that sets its own brightness keeps it; the clock mode never overrides, so it restores nothing.
     * Never touches Settings.System (S1796 ADR-2).
     */
    private fun setScreenBrightness(activity: Activity, value: Float) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
    }

    /**
     * S1903: a black screen that leaves the capacitive navigation keys glowing is not dark. Only this
     * window's override is touched, never a system setting - the same boundary S1796 ADR-2 drew for
     * screen brightness, and BRIGHTNESS_OVERRIDE_NONE hands the keys back to the platform on hide.
     *
     * A device with no button backlight simply has nothing to dim, which is the "if available" the
     * request asked for - the override is harmless there rather than needing a capability check.
     */
    private fun setButtonBacklight(activity: Activity, value: Float) {
        activity.window.attributes = activity.window.attributes.apply { buttonBrightness = value }
    }

    internal companion object {
        /** S3475: the sensor past the system lock, unless the player's rotation sensor is switched off. */
        internal fun dimOrientationFor(sensorEnabled: Boolean): Int = if (sensorEnabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }
}
