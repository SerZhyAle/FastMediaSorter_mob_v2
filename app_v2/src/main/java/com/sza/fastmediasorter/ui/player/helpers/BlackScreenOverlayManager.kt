package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.domain.repository.SettingsRepository
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

    private fun resolveEntryPoint(context: Context): DimClockEntryPoint =
        EntryPoints.get(context.applicationContext, DimClockEntryPoint::class.java)

    private suspend fun readDimClockEnabled(activity: Activity): Boolean {
        val repo = settingsRepositoryLazy?.get() ?: resolveEntryPoint(activity).settingsRepository()
        return repo.getSettings().flowOn(Dispatchers.IO).first().dimClockOverlayEnabled
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
        Timber.d("S3321: phone dim overlay shown, settings read dispatched off-main")
        scope.launch {
            val clockEnabled = readDimClockEnabled(activity)
            Timber.d("S3321: phone dim mode resolved, clockEnabled=$clockEnabled visible=$isVisible")
            // hide() may have won the race while the read was in flight.
            if (isVisible) applyDimMode(activity, clockEnabled)
        }
    }

    private fun applyDimMode(activity: Activity, clockEnabled: Boolean) {
        if (clockEnabled) {
            addClockView(activity)
        } else {
            setScreenBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        }
        Timber.d("S3256: phone black screen overlay shown, clockEnabled=$clockEnabled")
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
            bind(styleProvider, statusProvider, unitProvider)
        }
        decorView.addView(clockView)
        dimClockView = clockView
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val view = overlayView as? DimOverlayView
        return if (isVisible && view != null) {
            view.dispatchTouchEvent(event)
            true
        } else {
            false
        }
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

        setScreenBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)

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
     * S3256: overrides window screenBrightness to zero when dimmed screen clock is disabled,
     * and restores to default on un-dim. Never touches Settings.System (S1796 ADR-2).
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
}
