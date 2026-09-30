package com.sza.fastmediasorter.ui.systeminfo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivitySystemInfoBinding
import com.sza.fastmediasorter.ui.systeminfo.helpers.SystemInfoWindowManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
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
        super.onCreate(savedInstanceState)
        binding = ActivitySystemInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        systemInfoWindowManager.bind(this, binding)
        // Gathered on every creation, recreation included: the manager is unscoped and starts without a
        // report, so skipping this after a rotation left the window blank and its buttons inert.
        lifecycleScope.launch {
            val report = systemInfoWindowManager.gather(this@SystemInfoActivity)
            if (isFinishing || isDestroyed) return@launch
            systemInfoWindowManager.render(binding.systemInfoContent, report)
            binding.systemInfoCopy.requestFocus()
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, SystemInfoActivity::class.java)
    }
}
