package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.common.widget.DimHeadingProvider
import com.sza.fastmediasorter.ui.common.widget.DimOverlayView
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipActionRouter
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipIconLoader
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockInteractionHandler
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockOverlayView
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyleProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusContentProvider
import com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3154: the "screen off" action of the broadcast control screen has two outcomes.
 *
 * A build that declares the camera foreground-service type keeps a video session alive off-screen, so the
 * action really lets the display sleep. A store build declares no such type, so the camera dies the moment
 * the app leaves the screen; there the action draws a black overlay over the app instead - the same
 * experience as the Black Screen sub-program - while the display stays on and the session keeps streaming.
 */
@Singleton
class BroadcastBlankScreenManager @Inject constructor(
    private val controller: BroadcastSourceController,
    private val settingsRepository: Lazy<SettingsRepository>,
    private val dimClockStyleProvider: Lazy<DimClockStyleProvider>,
    private val dimStatusContentProvider: Lazy<DimStatusContentProvider>,
    private val dimChipIconLoader: Lazy<DimChipIconLoader>,
    private val dimChipActionRouter: Lazy<DimChipActionRouter>,
    private val dimClockInteractionHandler: Lazy<DimClockInteractionHandler>,
    private val dimHeadingProvider: Lazy<DimHeadingProvider>,
) {
    private var blanked = false

    // S3157: the scope is @Singleton because [blanked] must survive the activity recreation that a
    // configuration change performs, so every UI-bound field is held weakly. detach() still clears them
    // all - the weak reference is the backstop for a teardown path that is ever missed, not its
    // replacement. The strong owner while the UI lives is the view tree for the two views, and the
    // activity's own OnBackPressedDispatcher for the callback.
    private var hostRoot: WeakReference<View>? = null
    private var overlay: WeakReference<View>? = null
    private var dimClockView: WeakReference<DimClockOverlayView>? = null
    private var backCallback: WeakReference<OnBackPressedCallback>? = null

    /** True while the current session must blank rather than sleep, which is also what labels the button. */
    fun blanksInsteadOfSleeping(): Boolean {
        val live = controller.state.value as? BroadcastState.Live ?: return false
        return live.descriptor.mode != BroadcastMode.AUDIO_ONLY.name && !controller.cameraSurvivesBackground
    }

    /** Re-applies the overlay after a configuration change, which destroys and recreates the activity. */
    fun attach(activity: AppCompatActivity, screenRoot: View) {
        hostRoot = WeakReference(screenRoot)
        val callback = object : OnBackPressedCallback(blanked) {
            override fun handleOnBackPressed() {
                hide(activity)
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, callback)
        backCallback = WeakReference(callback)
        if (blanked) {
            show(activity)
        }
    }

    fun onScreenOffRequested(activity: AppCompatActivity) {
        if (blanksInsteadOfSleeping()) {
            blanked = true
            show(activity)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun detach() {
        hostRoot = null
        overlay = null
        dimClockView = null
        backCallback = null
    }

    private fun show(activity: AppCompatActivity) {
        if (overlay?.get() != null) return
        val content = hostRoot?.get()?.parent as? ViewGroup ?: return

        // Added above the screen's root rather than inside either orientation layout: the overlay must cover
        // the inset padding those layouts apply, and both orientations then need no duplicate view.
        val view = DimOverlayView(activity).apply {
            contentDescription = activity.getString(R.string.broadcast_control_blank_screen_cd)
            onExit = { hide(activity) }
            headingLookup = { dimHeadingProvider.get().current() }
        }
        dimHeadingProvider.get().setActive(true)
        content.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        overlay = WeakReference(view)
        Timber.d("S3370: broadcast blank screen dim shown")

        SystemBarsManager(activity).enterFullscreenMode()
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        backCallback?.get()?.isEnabled = true

        resolveDimMode(activity)
    }

    /**
     * S3321: the dim-clock flag is read off the main thread, and the black overlay no longer waits for
     * it. [onScreenOffRequested] runs on Main from a button tap, where a DataStore read that misses its
     * in-memory cache - the first one after process start, or after a low-memory trim - used to pay disk
     * latency inline. The flag is deliberately not cached on this @Singleton: the user can toggle it in
     * the settings Activity and come back to a session this manager outlived.
     */
    private fun resolveDimMode(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val clockEnabled = settingsRepository.get().getSettings()
                .flowOn(Dispatchers.IO)
                .first()
                .dimClockOverlayEnabled
            applyDimMode(activity, clockEnabled)
        }
    }

    private fun applyDimMode(activity: AppCompatActivity, clockEnabled: Boolean) {
        // hide() may have won the race while the read was in flight.
        if (overlay?.get() == null) return
        if (clockEnabled) {
            addClockView(activity)
        } else {
            setScreenBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        }
    }

    private fun addClockView(activity: AppCompatActivity) {
        // S3157: re-resolved rather than captured by the coroutine, so no strong UI reference outlives it.
        val content = hostRoot?.get()?.parent as? ViewGroup ?: return
        // A hide/show pair can land between the read and this call; a second clock view would be added
        // to the container with only the later one reachable for removal.
        if (dimClockView?.get() != null) return
        val clockView = DimClockOverlayView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            bind(
                dimClockStyleProvider.get(),
                dimStatusContentProvider.get(),
                null,
                dimChipIconLoader.get(),
                dimChipActionRouter.get(),
                dimClockInteractionHandler.get(),
            )
        }
        content.addView(clockView)
        // The overlay owns every touch while dimmed; taps reach the clock only through this forward.
        (overlay?.get() as? DimOverlayView)?.onUserActivity = { clockView.onHostInteraction() }
        // S3366: a chip or battery tap dismisses the dim overlay through the same path an exit
        // gesture takes, before the router starts the intent.
        clockView.onDimExitRequested = { hide(activity) }
        dimClockView = WeakReference(clockView)
    }

    private fun hide(activity: AppCompatActivity) {
        blanked = false
        backCallback?.get()?.isEnabled = false
        dimHeadingProvider.get().setActive(false)

        dimClockView?.get()?.let { clock ->
            (clock.parent as? ViewGroup)?.removeView(clock)
        }
        dimClockView = null

        overlay?.get()?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        overlay = null

        setScreenBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        setButtonBacklight(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)

        // S3157: built here rather than kept in a field, which would be an Activity held for the process
        // lifetime that the UiContextLeak detector cannot see. exitFullscreenMode() has no early return on
        // its own isFullscreenMode, so a fresh instance restores the bars exactly as a retained one did.
        SystemBarsManager(activity).exitFullscreenMode()
    }

    /**
     * S3256: overrides window screenBrightness to zero when dimmed screen clock is disabled,
     * and restores to default on un-dim. Never touches Settings.System (S1796 ADR-2).
     */
    private fun setScreenBrightness(activity: AppCompatActivity, value: Float) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
    }

    private fun setButtonBacklight(activity: AppCompatActivity, value: Float) {
        activity.window.attributes = activity.window.attributes.apply { buttonBrightness = value }
    }
}
