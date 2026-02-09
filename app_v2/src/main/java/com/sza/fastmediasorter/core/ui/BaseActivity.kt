package com.sza.fastmediasorter.core.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
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

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException("Binding is only valid between onCreateView and onDestroyView")

    abstract fun getViewBinding(): VB
    abstract fun setupViews()
    abstract fun observeData()

    override fun attachBaseContext(newBase: Context) {
        try {
            super.attachBaseContext(LocaleHelper.applyLocale(newBase))
        } catch (e: Exception) {
            // Handle NoSuchMethodException: rebase() on some Android versions
            // This is a known issue with AndroidX AppCompat
            Timber.w(e, "Failed to apply locale in attachBaseContext, using fallback")
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate: ${this::class.simpleName}")
        
        // Ensure proper system bar handling (combined with android:fitsSystemWindows="true" in layouts)
        // This prevents app content from going behind status/navigation bars on all Android versions
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        _binding = getViewBinding()
        setContentView(binding.root)
        
        // Apply keep screen awake if needed (will be controlled by settings)
        applyKeepScreenAwake()
        
        // Defer heavy initialization to allow first frame to render quickly
        binding.root.post {
            setupViews()
            observeData()
        }
    }

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

    private fun applyKeepScreenAwake() {
        if (shouldKeepScreenAwake()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
