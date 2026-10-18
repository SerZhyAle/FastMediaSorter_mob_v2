package com.sza.fastmediasorter.ui.systeminfo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivitySystemInfoBinding
import com.sza.fastmediasorter.ui.common.help.HelpOpenManager
import com.sza.fastmediasorter.ui.systeminfo.helpers.SystemInfoWindowManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Host for the full System information screen.
 */
@AndroidEntryPoint
class SystemInfoActivity : AppCompatActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var systemInfoWindowManager: SystemInfoWindowManager
    private lateinit var binding: ActivitySystemInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge on every API level, so the inset padding the window manager applies is the only
        // offset: API 35+ forces it, older levels would otherwise add a status-bar fallback on top of the decor.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // S4151: the saved colour theme must reach the window background before the layout inflates,
        // as in BaseActivity, or the report would not match the other full-screen screens.
        ColorThemePrefs.applyThemeOverlay(this)
        binding = ActivitySystemInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        systemInfoWindowManager.bind(this, binding)
        // Gathered on every creation, recreation included: the manager is unscoped and starts without a
        // report, so skipping this after a rotation left the window blank and its buttons inert.
        lifecycleScope.launch {
            val report = systemInfoWindowManager.gather(this@SystemInfoActivity)
            if (isFinishing || isDestroyed) return@launch
            systemInfoWindowManager.render(binding, report)
            binding.systemInfoSearchView.requestFocus()
        }
    }

    // Not a BaseActivity, so F1 reaches the help page here instead of through the shared key handling.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F1 &&
            HelpOpenManager.openIfPresent(this, SystemInfoActivity::class.java.simpleName, null)
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, SystemInfoActivity::class.java)
    }
}
