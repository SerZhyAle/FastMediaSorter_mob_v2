package com.sza.fastmediasorter_v2.core.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import timber.log.Timber

/**
 * Base Activity that provides common functionality for all activities.
 * - Handles keep screen awake
 * - Provides logging
 * - Manages ViewBinding lifecycle
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException("Binding is only valid between onCreateView and onDestroyView")

    abstract fun getViewBinding(): VB
    abstract fun setupViews()
    abstract fun observeData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate: ${this::class.simpleName}")
        
        _binding = getViewBinding()
        setContentView(binding.root)
        
        // Apply keep screen awake if needed (will be controlled by settings)
        applyKeepScreenAwake()
        
        setupViews()
        observeData()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        Timber.d("onDestroy: ${this::class.simpleName}")
    }

    protected open fun shouldKeepScreenAwake(): Boolean = true

    private fun applyKeepScreenAwake() {
        if (shouldKeepScreenAwake()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
