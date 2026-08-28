package com.sza.fastmediasorter.ui.blackscreen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityBlackScreenBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Autonomous Black Screen application surface (strategic S2211).
 * The whole window becomes pitch black, button brightness is turned off, and any user interaction finishes the screen.
 */
@AndroidEntryPoint
class BlackScreenActivity : BaseActivity<ActivityBlackScreenBinding>() {

    private lateinit var systemBarsManager: SystemBarsManager

    override fun getViewBinding(): ActivityBlackScreenBinding =
        ActivityBlackScreenBinding.inflate(layoutInflater)

    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemBarsManager = SystemBarsManager(this)
        systemBarsManager.enterFullscreenMode()
        setButtonBacklight(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        Timber.d("BlackScreenActivity: opened (fullscreen=true, brightness=OFF)")
    }

    override fun setupViews() {
        binding.blackScreenRoot.setOnClickListener { finish() }
    }

    override fun observeData() {
        // No data streams needed for static black screen
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        finish()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        setButtonBacklight(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        systemBarsManager.exitFullscreenMode()
        super.onDestroy()
    }

    private fun setButtonBacklight(value: Float) {
        window.attributes = window.attributes.apply { buttonBrightness = value }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, BlackScreenActivity::class.java)
    }
}
