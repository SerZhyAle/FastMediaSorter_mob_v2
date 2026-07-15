package com.sza.fastmediasorter.core.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.FocusFinder
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.input.GamepadNavIntent
import com.sza.fastmediasorter.core.input.GamepadNavigationTranslator
import com.sza.fastmediasorter.core.input.TvKeyRouter
import com.sza.fastmediasorter.core.input.TvNavAction
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.common.ActivityMouseDispatchHelper
import com.sza.fastmediasorter.ui.common.input.FocusTargetResolver
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs
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

    // S0508: one stateful translator per Activity (holds the held-direction repeat counter).
    private val gamepadNavigationTranslator by lazy(LazyThreadSafetyMode.NONE) { GamepadNavigationTranslator() }

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
     * Override to disable shared edge-to-edge setup for activities that handle their own window
     * insets (e.g. PlayerActivity which has custom immersive mode).
     */
    protected open fun shouldEnableEdgeToEdge(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Content draws behind system bars; each Activity applies its own WindowInsets padding.
        if (shouldEnableEdgeToEdge()) {
            enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)
        Timber.d("onCreate: ${this::class.simpleName}")

        // S0538: shrink dialog action buttons to 50% when "Compact elements (global)" is on, app-wide.
        // Must run before any dialog inflates (its button style reads the size theme attr at inflate).
        PlayerLayoutModePrefs.applyCompactDialogButtonsOverlay(this)

        // S0569: layer the saved custom accent theme overlay onto this Activity before any view or
        // dialog inflates and resolves color attributes. No-op for AUTO/LIGHT/DARK.
        ColorThemePrefs.applyThemeOverlay(this)

        _binding = getViewBinding()
        setContentView(binding.root)
        
        // Apply keep screen awake from the cached decision, then keep it in sync with settings.
        applyKeepScreenAwake()
        collectOnLifecycle(keepScreenSettingsRepository.getSettings()) { settings ->
            keepScreenAwakeDecision = keepScreenAwakeFor(settings)
            applyKeepScreenAwake()
            // S1045: drive the secure flag from the same settings stream (initial + reactive apply).
            lastSecureFlagSettings = settings
            applySecureFlagIfEnabled(settings)
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
            // S0504: redirect off a scrollable/paged container to the first real control inside it,
            // so D-pad/keyboard focus never parks on a bare container with the controls unreachable.
            val needsInitialFocus = shouldRequestInitialFocus()
            if (needsInitialFocus) {
                // S0944: fall back to the content root when a screen declares no explicit initial
                // focus, so every screen lands focus on a real control (resolver skips scroll
                // containers / EditText) instead of leaving a dead screen with the first key wasted.
                (getInitialFocusView() ?: _binding?.root)
                    ?.let { FocusTargetResolver.resolveToLeafFocusable(it) ?: it }
                    ?.requestFocus()
            }
            showGmsWarningIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reapply wake lock in case it was cleared by system
        applyKeepScreenAwake()
        // S1045: some OEMs clear window flags on background - re-apply from the last settings snapshot.
        lastSecureFlagSettings?.let { applySecureFlagIfEnabled(it) }
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

    // S1045: subclasses exposing credentials override to true so the secure flag is applied when the
    // user setting is ON. Default false clears the flag for ordinary screens.
    protected open fun isSensitiveScreen(): Boolean = false

    // S1045: last settings snapshot so onResume can re-apply the secure flag synchronously (mirrors
    // keepScreenAwakeDecision), since some OEMs clear window flags while the Activity is backgrounded.
    private var lastSecureFlagSettings: AppSettings? = null

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
        // S0508: gamepad/joystick stick navigation runs before mouse routing. Left stick moves
        // focus (D-pad equivalent), right stick scrolls the active container. Player-family hosts
        // route motion through their own dispatcher and opt out via shouldHandleGamepadNavigation().
        if (_binding != null && shouldHandleGamepadNavigation() && handleGamepadNavigation(event)) {
            return true
        }
        // S0289 safety net: generic motion (wheel/hover) from a finger is impossible in
        // practice, but the guard mirrors dispatchTouchEvent so a future regression cannot
        // silently consume finger events here either.
        val isFinger = event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
        // S0996: the mouse wheel must scroll the view under the pointer natively FIRST. Routing the
        // wheel through the Activity helper pre-order (like hover/buttons) let handleScroll's
        // unconditional consume swallow every wheel event, so any scrollable the helper could not
        // resolve as a target - nested settings content under a ViewPager2, screens with no
        // getMouseScrollTargetView override, any unfocused list under the cursor - never scrolled.
        // Let super dispatch the wheel to the hovered view; only when nothing there consumes it,
        // fall back to the helper's manual scroll of the explicit/focused container (S0289 case).
        if (!isFinger && _binding != null && event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val nativeConsumed = super.dispatchGenericMotionEvent(event)
            Timber.d("S0996: wheel native-first superConsumed=%b", nativeConsumed)
            if (nativeConsumed) return true
            return activityMouseDispatchHelper.handleGenericMotionEvent(event)
        }
        if (!isFinger && _binding != null && activityMouseDispatchHelper.handleGenericMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /**
     * Apply one analog-stick sample as focus movement or container scroll. Returns true only when
     * focus actually moved/redirected or a scroll target consumed the scroll - otherwise the event
     * falls through to mouse routing and super. S0508.
     */
    private fun handleGamepadNavigation(event: MotionEvent): Boolean {
        return when (val intent = gamepadNavigationTranslator.translate(event)) {
            is GamepadNavIntent.FocusMove -> moveFocusForGamepad(intent.direction)
            is GamepadNavIntent.Scroll -> {
                val target = getGamepadScrollTargetView() ?: return false
                target.scrollBy(intent.dx, intent.dy)
                true
            }
            null -> false
        }
    }

    private fun moveFocusForGamepad(direction: Int): Boolean {
        val root = _binding?.root as? ViewGroup ?: return false
        val current = currentFocus
        // S0506: if focus is parked on a scrollable/paged container, enter the first real control
        // first so the stick impulse is not wasted.
        if (FocusTargetResolver.isFocusTrapContainer(current)) {
            val source = current ?: getInitialFocusView() ?: root
            val leaf = FocusTargetResolver.resolveToLeafFocusable(source)
            if (leaf != null && leaf !== current && leaf.requestFocus()) return true
        }
        val next = FocusFinder.getInstance().findNextFocus(root, current, direction)
        return next != null && next !== current && next.requestFocus()
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

    // S1045: block screenshots / Recents preview on credential-bearing screens when the setting is ON.
    private fun applySecureFlagIfEnabled(settings: AppSettings) {
        val secure = isSensitiveScreen() && settings.secureSensitiveScreens
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
        if (guardContainerFocusOnDirectionalKey(event)) return true
        val action = tvKeyRouter.route(event)
        if (action != null) {
            if (onTvNavigation(action)) return true
        }
        // S0508: unconsumed gamepad L1/R1 page-jumps. Positioned after TvKeyRouter and after any
        // subclass override has already returned (subclasses call super last), so a screen that
        // consumes L1/R1 itself (browser tab switch, player transport) pre-empts this fallback.
        if (handleGamepadShoulderPageJump(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * The input-help surface for this screen, or null when F1 help does not apply (default).
     * Screens that consume F1 in their own onKeyDown (player / browse / settings handlers) leave
     * this null - their handler fires first and this fallback is never reached. Screens without an
     * own F1 handler override this so the global F1 key (S0510) opens the per-surface help dialog.
     */
    protected open fun getInputHelpSurface(): UiSurface? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F1) {
            getInputHelpSurface()?.let { surface ->
                InputHelpDialogFragment.show(supportFragmentManager, surface)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleGamepadShoulderPageJump(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!shouldHandleGamepadNavigation()) return false
        val fromGamepad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!fromGamepad) return false
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_R1 -> true
            KeyEvent.KEYCODE_BUTTON_L1 -> false
            else -> return false
        }
        return onGamepadPageJump(forward)
    }

    /**
     * Page-jump on an unconsumed gamepad shoulder button. Default page-scrolls
     * [getGamepadScrollTargetView] by roughly one viewport; pager/tab screens override for true
     * page/tab change. Returns false (no scroll target) so the key falls through unchanged. S0508.
     */
    protected open fun onGamepadPageJump(forward: Boolean): Boolean {
        val target = getGamepadScrollTargetView() ?: return false
        val page = target.height.takeIf { it > 0 } ?: return false
        target.scrollBy(0, if (forward) page else -page)
        return true
    }

    /**
     * Runtime counterpart to the S0504 initial-focus redirect: if the first directional key on a
     * non-touch device lands while focus is parked on a scrollable/paged container (or nowhere),
     * pull focus onto the first real control inside it and consume that key. Subsequent keys follow
     * normal traversal. No-op in touch mode, on screens that opt out via [shouldGuardContainerFocus],
     * and whenever focus already sits on a real control. S0506.
     */
    private fun guardContainerFocusOnDirectionalKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!isDirectionalKey(event.keyCode)) return false
        if (!shouldGuardContainerFocus()) return false
        if (!isNonTouchInputActive()) return false
        val focused = currentFocus
        if (!FocusTargetResolver.isFocusTrapContainer(focused)) return false
        val source = focused ?: getInitialFocusView() ?: _binding?.root ?: return false
        val leaf = FocusTargetResolver.resolveToLeafFocusable(source) ?: return false
        return leaf !== focused && leaf.requestFocus()
    }

    private fun isDirectionalKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT -> true
        else -> false
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
     * Opt-out for the shared gamepad/joystick navigation layer (see [dispatchGenericMotionEvent]).
     * Default true: the left stick moves focus and the right stick scrolls on this screen. Screens
     * that own their own motion routing (e.g. the player transport) override this to false. S0508.
     */
    protected open fun shouldHandleGamepadNavigation(): Boolean = true

    /**
     * Target container for right-stick scrolling. Defaults to the mouse-wheel scroll target so a
     * screen wiring one input also gets the other; screens can override to point elsewhere. S0508.
     */
    protected open fun getGamepadScrollTargetView(): View? = getMouseScrollTargetView()

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
    protected open fun shouldRequestInitialFocus(): Boolean = isNonTouchInputActive()

    /**
     * True when the user is driving the UI with a non-touch input device (TV remote / D-pad,
     * Quest3 controller, gamepad, attached mouse, or hardware keyboard). Shared by
     * [shouldRequestInitialFocus] (initial focus on open) and the dispatch-time container guard in
     * [dispatchKeyEvent] so both react to the same signal and cannot drift. S0289 / S0506.
     */
    protected fun isNonTouchInputActive(): Boolean {
        if (isTvDevice()) return true
        val notTouchMode = window?.decorView?.isInTouchMode == false
        val hasHardwareKeyboard = resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
        return notTouchMode || hasHardwareKeyboard
    }

    /**
     * Opt-out for the dispatch-time focus-container guard (see [dispatchKeyEvent]). Default true:
     * a directional key that lands on a scrollable/paged container first redirects focus to the
     * first real control inside it. Screens that own their key/focus routing (e.g. the player
     * transport) override this to false. S0506.
     */
    protected open fun shouldGuardContainerFocus(): Boolean = true
}
