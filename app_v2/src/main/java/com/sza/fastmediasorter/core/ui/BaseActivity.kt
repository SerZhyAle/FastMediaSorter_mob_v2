package com.sza.fastmediasorter.core.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.input.TvKeyRouter
import com.sza.fastmediasorter.core.input.TvNavAction
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.common.ActivityMouseDispatchHelper
import com.sza.fastmediasorter.utils.collectOnLifecycle
import timber.log.Timber
import javax.inject.Inject

/**
 * Base Activity that provides common functionality for all activities.
 * - Handles keep screen awake
 * - Provides logging
 * - Manages ViewBinding lifecycle
 * - Applies locale
 * - Provides centralised TV / keyboard navigation dispatch via [TvKeyRouter] (S0230)
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    companion object {
        // Show GMS warning at most once per process lifetime
        private var gmsWarningShown = false
    }

    // S0230: field-injected so Hilt can supply the singleton without constructor changes.
    @Inject
    lateinit var tvKeyRouter: TvKeyRouter

    // S0438: field-injected to drive the keep-screen-on flag from persisted settings.
    // Distinct name avoids hiding subclasses' own `settingsRepository` injections.
    @Inject
    lateinit var keepScreenSettingsRepository: SettingsRepository

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException("Binding is only valid between onCreateView and onDestroyView")

    abstract fun getViewBinding(): VB
    abstract fun setupViews()
    abstract fun observeData()

    // True once setupViews()/observeData() finished. Required because BaseActivity defers
    // setupViews() to binding.root.post { } so the first frame renders fast - meaning
    // Activity.onResume() can fire BEFORE lateinit managers from setupViews() are initialised.
    private var viewsReady = false
    private var resumePending = false

    private val activityMouseDispatchHelper by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMouseDispatchHelper(
            rootViewProvider = { _binding?.root },
            focusedViewProvider = { currentFocus },
            scrollTargetProvider = { getMouseScrollTargetView() },
            onContextClick = { view, x, y -> onMouseContextClick(view, x, y) },
            onMiddleClick = { view -> onMouseMiddleClick(view) },
            onNavigateBack = { view -> onMouseNavigateBack(view) },
            onNavigateForward = { view -> onMouseNavigateForward(view) },
        )
    }

    override fun attachBaseContext(newBase: Context) {
        try {
            val t0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
            val ctx = LocaleHelper.applyLocale(newBase)
            if (BuildConfig.DEBUG) {
                val dt = SystemClock.uptimeMillis() - t0
                Timber.d("BaseActivity.attachBaseContext[${this::class.simpleName}]: applyLocale took ${dt}ms")
            }
            super.attachBaseContext(ctx)
        } catch (e: Exception) {
            // Handle NoSuchMethodException: rebase() on some Android versions
            // This is a known issue with AndroidX AppCompat
            Timber.w(e, "Failed to apply locale in attachBaseContext, using fallback")
            super.attachBaseContext(newBase)
        }
    }

    /**
     * Override to disable edge-to-edge for activities that handle their own window insets 
     * (e.g. PlayerActivity which has custom immersive mode).
     */
    protected open fun shouldEnableEdgeToEdge(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge rendering: content draws behind system bars.
        // Each Activity applies its own WindowInsets padding via ViewCompat.setOnApplyWindowInsetsListener.
        if (shouldEnableEdgeToEdge()) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)
        Timber.d("onCreate: ${this::class.simpleName}")

        _binding = getViewBinding()
        setContentView(binding.root)
        
        // Apply keep screen awake from the cached decision, then keep it in sync with settings.
        applyKeepScreenAwake()
        collectOnLifecycle(keepScreenSettingsRepository.getSettings()) { settings ->
            keepScreenAwakeDecision = keepScreenAwakeFor(settings)
            applyKeepScreenAwake()
        }
        
        // Defer heavy initialization to allow first frame to render quickly
        val onCreateT0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
        binding.root.post {
            // The post can outlive the Activity when it is destroyed within the first frame
            // (e.g. WelcomeActivity forces a night-mode recreate in onCreate). onDestroy() has
            // already nulled _binding by then, so bail out before setupViews() touches it.
            if (_binding == null || isDestroyed) return@post
            if (BuildConfig.DEBUG) {
                val waitMs = SystemClock.uptimeMillis() - onCreateT0
                Timber.d("BaseActivity.setupViews[${this::class.simpleName}]: START (waited ${waitMs}ms for first frame)")
            }
            val setupT0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
            setupViews()
            if (BuildConfig.DEBUG) {
                val setupMs = SystemClock.uptimeMillis() - setupT0
                Timber.d("BaseActivity.setupViews[${this::class.simpleName}]: done in ${setupMs}ms")
            }
            observeData()
            viewsReady = true
            if (resumePending) {
                resumePending = false
                onResumeWithViews()
            }
            // S0230 + S0289: set initial focus on any non-touch input device.
            val needsInitialFocus = shouldRequestInitialFocus()
            if (needsInitialFocus) {
                getInitialFocusView()?.requestFocus()
            }
            showGmsWarningIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reapply wake lock in case it was cleared by system
        applyKeepScreenAwake()
        if (viewsReady) {
            onResumeWithViews()
        } else {
            // setupViews() not run yet - defer until the post{} block finishes.
            resumePending = true
        }
    }

    override fun onPause() {
        super.onPause()
        // Drop a pending resume if the Activity was paused before setupViews() finished:
        // onResumeWithViews() must not fire after the first onPause.
        resumePending = false
    }

    /**
     * Lifecycle hook for resume work that depends on lateinit fields initialised in setupViews().
     * Subclasses should override this instead of onResume() when they need access to those fields.
     * Guaranteed to fire only after setupViews()/observeData() have completed.
     */
    protected open fun onResumeWithViews() {}

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        Timber.d("onDestroy: ${this::class.simpleName}")
    }

    // S0438: keep-screen-on decision is settings-driven. Default applies the global preventSleep;
    // player hosts override to also honour the dependent keepScreenOnPlayer setting.
    protected open fun keepScreenAwakeFor(settings: AppSettings): Boolean = settings.preventSleep

    // Cached decision so onCreate/onResume can re-apply the flag synchronously between settings emissions.
    private var keepScreenAwakeDecision: Boolean = true

    /**
     * Called when the device configuration changes (e.g., screen rotation).
     * Override this method in subclasses to handle layout recalculations
     * when the screen orientation changes.
     * 
     * This is used to support rotation on phones - when width > height,
     * we treat it as landscape mode (same as tablet native mode).
     */
    protected open fun onLayoutConfigurationChanged(newConfig: Configuration) {
        // Default: do nothing. Subclasses can override to recalculate layouts.
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d("onConfigurationChanged: ${this::class.simpleName}, orientation=${if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"}, screenWidthDp=${newConfig.screenWidthDp}")
        
        // Notify subclasses to handle layout changes after rotation.
        // Guard the same destroyed-before-post race as onCreate(): the runnable must not
        // reach a subclass that dereferences a cleared binding.
        binding.root.post {
            if (_binding == null || isDestroyed) return@post
            onLayoutConfigurationChanged(newConfig)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // S0289 safety net: never route finger taps through the mouse helper. MouseEventHandler
        // already filters by getToolType(), but adding the guard here makes any future helper
        // method automatically safe against the same regression (emulator/Quest3/touchpad TVs
        // can mark touch events with SOURCE_MOUSE in event.source, which used to consume UP
        // and break click delivery to every interactive view in every Activity).
        val isFinger = ev?.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
        if (!isFinger && _binding != null && activityMouseDispatchHelper.handleTouchEvent(ev)) {
            return true
        }
        ev?.let {
            if (it.action == MotionEvent.ACTION_DOWN) {
                com.sza.fastmediasorter.utils.UserActionLogger.logTouch(
                    action = "DOWN",
                    x = it.x,
                    y = it.y,
                    context = this::class.simpleName ?: "UnknownActivity"
                )
            } else if (it.action == MotionEvent.ACTION_UP) {
                com.sza.fastmediasorter.utils.UserActionLogger.logTouch(
                    action = "UP",
                    x = it.x,
                    y = it.y,
                    context = this::class.simpleName ?: "UnknownActivity"
                )
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // S0289 safety net: generic motion (wheel/hover) from a finger is impossible in
        // practice, but the guard mirrors dispatchTouchEvent so a future regression cannot
        // silently consume finger events here either.
        val isFinger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
        if (!isFinger && _binding != null && activityMouseDispatchHelper.handleGenericMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun showGmsWarningIfNeeded() {
        if (gmsWarningShown || GmsAvailabilityChecker.isOk) return
        gmsWarningShown = true
        // Persistent guard: show snackbar at most once per installation.
        if (GmsAvailabilityChecker.isWarningSeen(this)) return
        GmsAvailabilityChecker.markWarningSeen(this)
        val msgRes = if (GmsAvailabilityChecker.needsUpdate)
            R.string.gms_update_required
        else
            R.string.gms_unavailable
        Snackbar.make(binding.root, msgRes, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.gms_update_action) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.android.gms")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")))
                }
            }
            .show()
    }

    private fun applyKeepScreenAwake() {
        if (keepScreenAwakeDecision) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── S0230: TV / keyboard navigation ──────────────────────────────────────

    /**
     * Centralised key dispatch for TV remotes and physical keyboards.
     *
     * Routing order:
     * 1. Route through [TvKeyRouter]; if it produces a [TvNavAction], offer to
     *    [onTvNavigation] - if consumed, return true.
     * 2. Fall through to [super.dispatchKeyEvent] (Android focus traversal,
     *    gamepad via [GamepadInputManager] in subclasses, etc.).
     *
     * Subclasses that need to intercept key events BEFORE this (e.g. PlayerActivity)
     * override [dispatchKeyEvent] themselves and call super only when they have not
     * consumed the event - the subclass override naturally shadows this implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = tvKeyRouter.route(event)
        if (action != null) {
            if (onTvNavigation(action)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Hook for subclasses to react to TV remote / keyboard semantic actions.
     *
     * @return true if the action was fully consumed and should not be propagated further.
     * Default: false (let Android handle focus traversal natively).
     */
    protected open fun onTvNavigation(action: TvNavAction): Boolean = false

    /**
     * Return the [View] that should receive initial focus when the Activity opens on a TV.
     *
     * Called after [setupViews] completes, only on TV devices ([isTvDevice]).
     * Default: null (rely on Android's automatic focus on first focusable view).
     *
     * RecyclerView-based screens typically do not need to override this -
     * Android's focus traversal picks the first item automatically.
     * Non-list screens with a clear primary action button should return that button.
     */
    protected open fun getInitialFocusView(): View? = null

    /**
     * Optional explicit target for mouse-wheel scrolling on simple screens.
     * Default is null so complex surfaces can keep their bespoke wheel routing.
     */
    protected open fun getMouseScrollTargetView(): View? = null

    /**
     * Keyboard/D-pad focus can land on a non-clickable child inside a clickable row or card.
     * Walk up the parent chain so Enter/Center activates the owning control instead of no-oping.
     */
    protected fun activateFocusedViewOrAncestor(startView: View? = currentFocus): Boolean {
        var candidate = startView
        while (candidate != null) {
            if (candidate.performClick()) return true
            candidate = candidate.parent as? View
        }
        return false
    }

    /**
     * Default context-click behaviour: long-click the current interaction target.
     */
    protected open fun onMouseContextClick(view: View, x: Float, y: Float) {
        if (!view.performLongClick()) {
            _binding?.root?.performLongClick()
        }
    }

    /**
     * Middle-click is a no-op by default; richer surfaces override it explicitly.
     */
    protected open fun onMouseMiddleClick(view: View) = Unit

    /**
     * Back-button mouse input maps to the Activity back stack by default.
     */
    protected open fun onMouseNavigateBack(view: View) {
        onBackPressedDispatcher.onBackPressed()
    }

    /**
     * Forward-button mouse input is screen-specific, so the base contract stays no-op.
     */
    protected open fun onMouseNavigateForward(view: View) = Unit

    /**
     * True when the app is running on a TV device (Android TV / Google TV / Fire TV).
     *
     * Uses the dual-check recommended by `developer.android.com/training/tv/get-started/hardware`:
     * - Primary: `PackageManager.FEATURE_LEANBACK` - system feature declared by genuine TV ROMs.
     * - Secondary: `Configuration.UI_MODE_TYPE_TELEVISION` - covers older / non-Leanback TV systems.
     *
     * Why both: `FEATURE_LEANBACK` is more resistant to "fake TV" boxes and phones with HDMI-out
     * that mis-report UI mode; `UI_MODE_TYPE_TELEVISION` catches stripped-down TV systems that omit
     * Leanback. Either positive signal is enough.
     */
    protected fun isTvDevice(): Boolean {
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val isTvUiMode = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
        return hasLeanback || isTvUiMode
    }

    /**
     * True when the Activity should auto-request initial focus on the view returned by
     * [getInitialFocusView]. Broader than [isTvDevice] - also covers Quest3 controllers
     * (which report touch-mode=false on D-pad input) and phones with a connected hardware
     * keyboard.
     *
     * Trigger conditions (any one is enough):
     * - [isTvDevice] returns true (Android TV / Google TV / Fire TV via Leanback or TV UI mode).
     * - The window decor is not in touch mode (Quest3 controllers, gamepads, attached mice).
     * - The active configuration reports a hardware keyboard present.
     *
     * Subclasses can override to opt out (return false) on screens where forced initial focus
     * would be disruptive. S0289.
     */
    protected open fun shouldRequestInitialFocus(): Boolean {
        if (isTvDevice()) return true
        val notTouchMode = window?.decorView?.isInTouchMode == false
        val hasHardwareKeyboard = resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
        return notTouchMode || hasHardwareKeyboard
    }
}
