package com.sza.fastmediasorter.ui.remotecontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import androidx.activity.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityExternalControlBinding
import com.sza.fastmediasorter.ui.remotecontrol.helpers.ExternalControlScreenManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExternalControlActivity : BaseActivity<ActivityExternalControlBinding>() {
    private val viewModel: ExternalControlViewModel by viewModels()
    private var screen: ExternalControlScreenManager? = null

    override fun getViewBinding(): ActivityExternalControlBinding = ActivityExternalControlBinding.inflate(
        layoutInflater
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) intent.removeExtra(EXTRA_OPEN_CONTROLLERS)
    }

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        screen = ExternalControlScreenManager(this, binding, viewModel).also {
            it.bind(intent.getBooleanExtra(EXTRA_OPEN_CONTROLLERS, false))
        }
    }

    override fun observeData() = Unit

    override fun getMouseScrollTargetView(): View = binding.externalControlScroll

    override fun getInitialFocusView(): View = binding.externalControlSections.rowExternalControlListening

    override fun getHelpSurfaceId(): String = screen?.focusedHelpSurface() ?: "ExternalControlActivity"

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_help_action, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        screen?.handleKey(event) == true || super.dispatchKeyEvent(event)

    companion object {
        private const val EXTRA_OPEN_CONTROLLERS = "open_controllers"

        fun intent(context: Context, openControllers: Boolean = false): Intent =
            Intent(context, ExternalControlActivity::class.java).putExtra(EXTRA_OPEN_CONTROLLERS, openControllers)
    }
}
