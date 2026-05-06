package com.sza.fastmediasorter.core.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.core.util.LocaleHelper
import timber.log.Timber

/**
 * Base Activity that provides common functionality for all activities.
 * - Handles keep screen awake
 * - Provides logging
 * - Manages ViewBinding lifecycle
 * - Applies locale
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    companion object {
        // Show GMS warning at most once per process lifetime
        private var gmsWarningShown = false
    }

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException("Binding is only valid between onCreateView and onDestroyView")

    abstract fun getViewBinding(): VB
    abstract fun setupViews()
    abstract fun observeData()

    // True once setupViews()/observeData() finished. Required because BaseActivity defers
    // setupViews() to binding.root.post { } so the first frame renders fast — meaning
    // Activity.onResume() can fire BEFORE lateinit managers from setupViews() are initialised.
    private var viewsReady = false
    private var resumePending = false

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
        
        // Apply keep screen awake if needed (will be controlled by settings)
        applyKeepScreenAwake()
        
        // Defer heavy initialization to allow first frame to render quickly
        val onCreateT0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
        binding.root.post {
            if (BuildConfig.DEBUG) {
                val waitMs = SystemClock.uptimeMillis() - onCreateT0
                Timber.d("BaseActivity.setupViews[${this::class.simpleName}]: START (waited ${waitMs}ms for first frame)")
            }
            val setupT0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
            setupViews()
            if (BuildConfig.DEBUG) {
                Timber.d("BaseActivity.setupViews[${this::class.simpleName}]: done in ${SystemClock.uptimeMillis() - setupT0}ms")
            }
            observeData()
            viewsReady = true
            if (resumePending) {
                resumePending = false
                onResumeWithViews()
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
            // setupViews() not run yet — defer until the post{} block finishes.
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

    protected open fun shouldKeepScreenAwake(): Boolean = true

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
        
        // Notify subclasses to handle layout changes after rotation
        binding.root.post {
            onLayoutConfigurationChanged(newConfig)
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        ev?.let {
            if (it.action == android.view.MotionEvent.ACTION_DOWN) {
                com.sza.fastmediasorter.utils.UserActionLogger.logTouch(
                    action = "DOWN", 
                    x = it.x, 
                    y = it.y, 
                    context = this::class.simpleName ?: "UnknownActivity"
                )
            } else if (it.action == android.view.MotionEvent.ACTION_UP) {
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
        if (shouldKeepScreenAwake()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
